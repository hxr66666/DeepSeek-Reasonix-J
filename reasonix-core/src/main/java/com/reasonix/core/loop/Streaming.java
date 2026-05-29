package com.reasonix.core.loop;

import com.reasonix.core.ports.ModelClient;
import com.reasonix.core.ports.ModelClient.ModelStreamChunk;
import com.reasonix.core.ports.ModelClient.ToolCall;
import com.reasonix.core.ports.ModelClient.Usage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class Streaming {

    private final Sinks.Many<ModelStreamChunk> sink;
    private final AtomicReference<StringBuilder> contentBuffer;
    private final AtomicReference<StringBuilder> reasoningBuffer;
    private final AtomicReference<List<ToolCall>> toolCallsBuffer;
    private final AtomicReference<Usage> usageBuffer;

    public Streaming() {
        this.sink = Sinks.many().multicast().onBackpressureBuffer();
        this.contentBuffer = new AtomicReference<>(new StringBuilder());
        this.reasoningBuffer = new AtomicReference<>(new StringBuilder());
        this.toolCallsBuffer = new AtomicReference<>(new ArrayList<>());
        this.usageBuffer = new AtomicReference<>(Usage.empty());
    }

    public void emitToken(String token) {
        contentBuffer.get().append(token);
        sink.tryEmitNext(new ModelStreamChunk(token, null, null, null));
    }

    public void emitReasoning(String reasoning) {
        reasoningBuffer.get().append(reasoning);
        sink.tryEmitNext(new ModelStreamChunk(null, null, reasoning, null));
    }

    public void emitToolCall(ToolCall toolCall) {
        toolCallsBuffer.get().add(toolCall);
        sink.tryEmitNext(new ModelStreamChunk(null, toolCall, null, null));
    }

    public void emitUsage(Usage usage) {
        usageBuffer.set(usage);
        sink.tryEmitNext(new ModelStreamChunk(null, null, null, usage));
    }

    public void complete() {
        sink.tryEmitComplete();
    }

    public void error(Throwable error) {
        sink.tryEmitError(error);
    }

    public Flux<ModelStreamChunk> asFlux() {
        return sink.asFlux();
    }

    public String getAccumulatedContent() {
        return contentBuffer.get().toString();
    }

    public String getAccumulatedReasoning() {
        return reasoningBuffer.get().toString();
    }

    public List<ToolCall> getAccumulatedToolCalls() {
        return new ArrayList<>(toolCallsBuffer.get());
    }

    public Usage getUsage() {
        return usageBuffer.get();
    }

    public void reset() {
        contentBuffer.set(new StringBuilder());
        reasoningBuffer.set(new StringBuilder());
        toolCallsBuffer.set(new ArrayList<>());
        usageBuffer.set(Usage.empty());
    }
}
