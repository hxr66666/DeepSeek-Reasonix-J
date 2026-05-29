package com.reasonix.core.loop;

import com.reasonix.core.context.ContextManager;
import com.reasonix.core.memory.AppendOnlyLog;
import com.reasonix.core.memory.ImmutablePrefix;
import com.reasonix.core.memory.VolatileScratch;
import com.reasonix.core.ports.EventSink;
import com.reasonix.core.ports.ModelClient;
import com.reasonix.core.ports.ModelClient.ChatMessage;
import com.reasonix.core.ports.ModelClient.ModelStreamChunk;
import com.reasonix.core.ports.ModelClient.Usage;
import com.reasonix.core.ports.ToolHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

public class CacheFirstLoop {

    private static final Logger log = LoggerFactory.getLogger(CacheFirstLoop.class);

    private final ImmutablePrefix prefix;
    private final AppendOnlyLog logEntries;
    private final VolatileScratch scratch;
    private final ToolHost tools;
    private final ModelClient modelClient;
    private final ContextManager contextManager;
    private final EventSink eventSink;
    private final String model;
    private final int maxIterations;

    public CacheFirstLoop(ImmutablePrefix prefix, AppendOnlyLog logEntries,
                          VolatileScratch scratch, ToolHost tools,
                          ModelClient modelClient, ContextManager contextManager,
                          EventSink eventSink, String model, int maxIterations) {
        this.prefix = prefix;
        this.logEntries = logEntries;
        this.scratch = scratch;
        this.tools = tools;
        this.modelClient = modelClient;
        this.contextManager = contextManager;
        this.eventSink = eventSink;
        this.model = model;
        this.maxIterations = maxIterations;
    }

    public Flux<EventSink.LoopEvent> run(String userMessage) {
        logEntries.append(ChatMessage.user(userMessage));
        scratch.reset();

        return Flux.defer(() -> {
            List<ModelClient.ToolCall> pendingToolCalls = new ArrayList<>();
            StringBuilder contentBuffer = new StringBuilder();
            StringBuilder reasoningBuffer = new StringBuilder();
            Usage[] lastUsage = {Usage.empty()};

            ModelClient.ChatRequest request = buildRequest();

            Flux<EventSink.LoopEvent> streamEvents = modelClient.chatStream(request)
                    .flatMap(chunk -> {
                        if (chunk.token() != null) {
                            contentBuffer.append(chunk.token());
                            return Flux.just((EventSink.LoopEvent) new EventSink.LoopEvent.TokenGenerated(chunk.token()));
                        }
                        if (chunk.reasoning() != null) {
                            reasoningBuffer.append(chunk.reasoning());
                            scratch.setReasoning(reasoningBuffer.toString());
                            return Flux.just((EventSink.LoopEvent) new EventSink.LoopEvent.ReasoningGenerated(chunk.reasoning()));
                        }
                        if (chunk.toolCall() != null) {
                            pendingToolCalls.add(chunk.toolCall());
                            return Flux.just((EventSink.LoopEvent) new EventSink.LoopEvent.ToolCallStarted(
                                    chunk.toolCall().id(), chunk.toolCall().name()));
                        }
                        if (chunk.usage() != null) {
                            lastUsage[0] = chunk.usage();
                        }
                        return Flux.<EventSink.LoopEvent>empty();
                    });

            Flux<EventSink.LoopEvent> postStream = Flux.defer(() -> {
                if (!pendingToolCalls.isEmpty()) {
                    return executeToolCalls(pendingToolCalls, contentBuffer.toString());
                }

                logEntries.append(ChatMessage.assistant(contentBuffer.toString()));
                eventSink.emit(new EventSink.LoopEvent.TurnEnd("session", lastUsage[0]));

                ContextManager.PostUsageDecision decision =
                        contextManager.decideAfterUsage(lastUsage[0], model);
                return handleDecision(decision);
            });

            return Flux.concat(streamEvents, postStream);
        });
    }

    private Flux<EventSink.LoopEvent> executeToolCalls(
            List<ModelClient.ToolCall> toolCalls, String assistantContent) {

        logEntries.append(ChatMessage.assistantWithTools(toolCalls));

        Dispatch dispatch = new Dispatch(tools, eventSink);
        return dispatch.dispatchToolCallsChunked(toolCalls)
                .collectList()
                .flatMapMany(results -> {
                    for (var result : results) {
                        logEntries.append(ChatMessage.toolResult(result.toolCallId(), result.content()));
                    }
                    return run("");
                });
    }

    private Flux<EventSink.LoopEvent> handleDecision(ContextManager.PostUsageDecision decision) {
        return switch (decision) {
            case ContextManager.PostUsageDecision.NoOp noOp -> Flux.empty();
            case ContextManager.PostUsageDecision.Fold fold -> {
                eventSink.emit(new EventSink.LoopEvent.ContextFold(
                        fold.ratio(), fold.tailFraction(), fold.aggressive()));
                yield Flux.empty();
            }
            case ContextManager.PostUsageDecision.ExitWithSummary exit -> {
                log.warn("Context exceeded force summary threshold: ratio={}", exit.ratio());
                yield Flux.empty();
            }
        };
    }

    private ModelClient.ChatRequest buildRequest() {
        List<ChatMessage> allMessages = new ArrayList<>();
        allMessages.addAll(prefix.getFewShots());
        allMessages.addAll(logEntries.toMessages());

        return ModelClient.ChatRequest.builder()
                .model(model)
                .systemPrompt(prefix.getSystem())
                .messages(allMessages)
                .toolSpecs(prefix.getToolSpecs())
                .build();
    }
}
