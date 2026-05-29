package com.reasonix.core.ports;

public interface EventSink {

    void emit(LoopEvent event);

    void flush();

    sealed interface LoopEvent {
        record TurnStart(String sessionId, String model) implements LoopEvent {}
        record TokenGenerated(String token) implements LoopEvent {}
        record ReasoningGenerated(String reasoning) implements LoopEvent {}
        record ToolCallStarted(String toolCallId, String toolName) implements LoopEvent {}
        record ToolCallCompleted(String toolCallId, String toolName, ToolHost.ToolResult result) implements LoopEvent {}
        record TurnEnd(String sessionId, ModelClient.Usage usage) implements LoopEvent {}
        record ContextFold(double ratio, double tailFraction, boolean aggressive) implements LoopEvent {}
        record Error(String message, Throwable cause) implements LoopEvent {}
    }
}
