package com.reasonix.core.loop;

import com.reasonix.core.ports.EventSink;
import com.reasonix.core.ports.ModelClient;
import com.reasonix.core.ports.ToolHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Dispatch {

    private static final Logger log = LoggerFactory.getLogger(Dispatch.class);

    private static final int PARALLEL_MAX = Integer.getInteger("REASONIX_PARALLEL_MAX", 3);
    private static final boolean FORCE_SERIAL = "serial".equals(System.getenv("REASONIX_TOOL_DISPATCH"));
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ToolHost tools;
    private final EventSink eventSink;

    public Dispatch(ToolHost tools, EventSink eventSink) {
        this.tools = tools;
        this.eventSink = eventSink;
    }

    public Flux<ToolHost.ToolResult> dispatchToolCallsChunked(List<ModelClient.ToolCall> calls) {
        if (FORCE_SERIAL) {
            return Flux.fromIterable(calls).concatMap(this::dispatchSingle);
        }

        List<List<ModelClient.ToolCall>> chunks = chunkByParallelSafety(calls);

        return Flux.fromIterable(chunks)
                .concatMap(chunk -> {
                    if (chunk.size() == 1) {
                        return dispatchSingle(chunk.get(0)).flux();
                    }
                    return dispatchParallel(chunk);
                });
    }

    private List<List<ModelClient.ToolCall>> chunkByParallelSafety(List<ModelClient.ToolCall> calls) {
        List<List<ModelClient.ToolCall>> chunks = new ArrayList<>();
        List<ModelClient.ToolCall> currentChunk = new ArrayList<>();

        for (var call : calls) {
            boolean isParallelSafe = isParallelSafe(call.name());

            if (!isParallelSafe && !currentChunk.isEmpty()) {
                chunks.add(new ArrayList<>(currentChunk));
                currentChunk.clear();
            }

            currentChunk.add(call);

            if (!isParallelSafe) {
                chunks.add(new ArrayList<>(currentChunk));
                currentChunk.clear();
            }
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk);
        }

        return chunks;
    }

    private Flux<ToolHost.ToolResult> dispatchParallel(List<ModelClient.ToolCall> calls) {
        int limit = Math.min(calls.size(), PARALLEL_MAX);
        List<ModelClient.ToolCall> toExecute = calls.subList(0, limit);

        return Flux.fromIterable(toExecute)
                .flatMap(call -> dispatchSingle(call)
                        .onErrorResume(e -> Mono.just(ToolHost.ToolResult.failure(call.id(), e.getMessage()))),
                        limit);
    }

    private Mono<ToolHost.ToolResult> dispatchSingle(ModelClient.ToolCall call) {
        return Mono.fromCallable(() -> {
                    Future<ToolHost.ToolResult> future = EXECUTOR.submit(() ->
                            tools.dispatch(call.name(), call.arguments()));
                    return future.get();
                })
                .doOnNext(result -> eventSink.emit(
                        new EventSink.LoopEvent.ToolCallCompleted(call.id(), call.name(), result)))
                .onErrorResume(e -> {
                    ToolHost.ToolResult failure = ToolHost.ToolResult.failure(call.id(), e.getMessage());
                    eventSink.emit(new EventSink.LoopEvent.ToolCallCompleted(call.id(), call.name(), failure));
                    return Mono.just(failure);
                });
    }

    private boolean isParallelSafe(String toolName) {
        return switch (toolName) {
            case "read_file", "list_directory", "directory_tree", "search_files",
                 "search_content", "get_file_info", "web_search", "web_fetch",
                 "recall_memory", "semantic_search", "run_skill", "spawn_subagent",
                 "job_output", "list_jobs" -> true;
            default -> false;
        };
    }
}
