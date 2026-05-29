package com.reasonix.core.transcript;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TranscriptLog {

    private static final Logger log = LoggerFactory.getLogger(TranscriptLog.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path filePath;

    public TranscriptLog(Path filePath) {
        this.filePath = filePath;
        try {
            Files.createDirectories(filePath.getParent());
        } catch (IOException e) {
            log.error("Failed to create transcript directory: {}", e.getMessage());
        }
    }

    public void append(String type, Map<String, Object> data) {
        try {
            Map<String, Object> entry = Map.of(
                    "timestamp", Instant.now().toString(),
                    "type", type,
                    "data", data
            );
            String line = MAPPER.writeValueAsString(entry) + "\n";
            Files.writeString(filePath, line, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Failed to write transcript: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> readAll() {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (!Files.exists(filePath)) return entries;

        try {
            List<String> lines = Files.readAllLines(filePath);
            for (String line : lines) {
                if (line.isBlank()) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> entry = MAPPER.readValue(line, Map.class);
                entries.add(entry);
            }
        } catch (IOException e) {
            log.error("Failed to read transcript: {}", e.getMessage());
        }
        return entries;
    }
}
