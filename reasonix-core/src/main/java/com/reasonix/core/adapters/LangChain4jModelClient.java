package com.reasonix.core.adapters;

import com.reasonix.core.ports.ModelClient;
import com.reasonix.core.ports.ModelClient.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;

public class LangChain4jModelClient implements ModelClient {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jModelClient.class);

    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingModel;

    public LangChain4jModelClient(ChatLanguageModel chatModel, StreamingChatLanguageModel streamingModel) {
        this.chatModel = chatModel;
        this.streamingModel = streamingModel;
    }

    @Override
    public Flux<ModelStreamChunk> chatStream(ChatRequest request) {
        Sinks.Many<ModelStreamChunk> sink = Sinks.many().multicast().onBackpressureBuffer();

        List<dev.langchain4j.data.message.ChatMessage> lc4jMessages = convertMessages(request);

        streamingModel.chat(lc4jMessages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                sink.tryEmitNext(new ModelStreamChunk(partialResponse, null, null, null));
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                if (response != null && response.tokenUsage() != null) {
                    TokenUsage tu = response.tokenUsage();
                    Usage usage = new Usage(
                            tu.inputTokenCount() != null ? tu.inputTokenCount() : 0,
                            tu.outputTokenCount() != null ? tu.outputTokenCount() : 0,
                            tu.totalTokenCount() != null ? tu.totalTokenCount() : 0,
                            0, 0
                    );
                    sink.tryEmitNext(new ModelStreamChunk(null, null, null, usage));
                }
                sink.tryEmitComplete();
            }

            @Override
            public void onError(Throwable error) {
                log.error("Streaming chat error", error);
                sink.tryEmitError(error);
            }
        });

        return sink.asFlux();
    }

    @Override
    public ModelResponse chat(ChatRequest request) {
        List<dev.langchain4j.data.message.ChatMessage> lc4jMessages = convertMessages(request);
        ChatResponse response = chatModel.chat(lc4jMessages);

        String content = response.aiMessage() != null ? response.aiMessage().text() : null;
        Usage usage = convertUsage(response.tokenUsage());

        return new ModelResponse(content, List.of(), null, usage, null);
    }

    private List<dev.langchain4j.data.message.ChatMessage> convertMessages(ChatRequest request) {
        List<dev.langchain4j.data.message.ChatMessage> lc4jMessages = new ArrayList<>();

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            lc4jMessages.add(SystemMessage.from(request.systemPrompt()));
        }

        for (var msg : request.messages()) {
            switch (msg.role()) {
                case "user" -> lc4jMessages.add(UserMessage.from(msg.content()));
                case "assistant" -> lc4jMessages.add(AiMessage.from(msg.content()));
                case "system" -> lc4jMessages.add(SystemMessage.from(msg.content()));
                default -> lc4jMessages.add(UserMessage.from(msg.content()));
            }
        }

        return lc4jMessages;
    }

    private Usage convertUsage(TokenUsage tu) {
        if (tu == null) return Usage.empty();
        return new Usage(
                tu.inputTokenCount() != null ? tu.inputTokenCount() : 0,
                tu.outputTokenCount() != null ? tu.outputTokenCount() : 0,
                tu.totalTokenCount() != null ? tu.totalTokenCount() : 0,
                0, 0
        );
    }
}
