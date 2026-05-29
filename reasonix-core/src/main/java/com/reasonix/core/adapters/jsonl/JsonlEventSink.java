package com.reasonix.core.adapters.jsonl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reasonix.core.ports.EventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;

public class JsonlEventSink implements EventSink {

    private static final Logger log = LoggerFactory.getLogger(JsonlEventSink.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path filePath;

    public JsonlEventSink(Path filePath) {
        this.filePath = filePath;
        try {
            Files.createDirectories(filePath.getParent());
            if (!Files.exists(filePath)) {
                Files.writeString(filePath, "", StandardOpenOption.CREATE);
            }
        } catch (IOException e) {
            log.error("Failed to initialize JSONL event sink: {}", e.getMessage());
        }
    }

    @Override
    public void emit(LoopEvent event) {
        try {
            Map<String, Object> entry = Map.of(
                    "timestamp", Instant.now().toString(),
                    "type", event.getClass().getSimpleName(),
                    "data", serializeEvent(event)
            );
            String line = MAPPER.writeValueAsString(entry) + "\n";
            Files.writeString(filePath, line, StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("Failed to write event to JSONL: {}", e.getMessage());
        }
    }

    @Override
    public void flush() {
        // JSONL writes are immediate, no buffering to flush
    }

    private Map<String, Object> serializeEvent(LoopEvent event) {
        return switch (event) {
            case LoopEvent.TurnStart e -> Map.of("sessionId", e.sessionId(), "model", e.model());
            case LoopEvent.TokenGenerated e -> Map.of("token", e.token());
            case LoopEvent.ReasoningGenerated e -> Map.of("reasoning", e.reasoning());
            case LoopEvent.ToolCallStarted e -> Map.of("toolCallId", e.toolCallId(), "toolName", e.toolName());
            case LoopEvent.ToolCallCompleted e -> Map.of("toolCallId", e.toolCallId(), "toolName", e.toolName(),
                    "isError", e.result().isError());
            case LoopEvent.TurnEnd e -> Map.of("sessionId", e.sessionId());
            case LoopEvent.ContextFold e -> Map.of("ratio", e.ratio(), "aggressive", e.aggressive());
            case LoopEvent.Error e -> Map.of("message", e.message());
        };
    }
}
