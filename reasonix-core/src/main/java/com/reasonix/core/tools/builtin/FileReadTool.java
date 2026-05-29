package com.reasonix.core.tools.builtin;

import com.reasonix.core.ports.ToolHost;
import com.reasonix.core.tools.ToolContext;
import com.reasonix.core.tools.ToolDefinition;
import com.reasonix.core.tools.ToolExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class FileReadTool implements ToolExecutor {

    @Override
    public ToolHost.ToolResult execute(String toolName, Map<String, Object> arguments, ToolContext context) {
        String filePath = (String) arguments.get("path");
        if (filePath == null) return ToolHost.ToolResult.failure("No path provided");

        Path resolved = resolvePath(filePath, context);
        if (!Files.exists(resolved)) return ToolHost.ToolResult.failure("File not found: " + resolved);

        try {
            List<String> lines = Files.readAllLines(resolved);
            int offset = arguments.containsKey("offset") ? ((Number) arguments.get("offset")).intValue() : 0;
            int limit = arguments.containsKey("limit") ? ((Number) arguments.get("limit")).intValue() : lines.size();

            int end = Math.min(offset + limit, lines.size());
            StringBuilder sb = new StringBuilder();
            for (int i = offset; i < end; i++) {
                sb.append(i + 1).append(": ").append(lines.get(i)).append("\n");
            }
            return ToolHost.ToolResult.success(sb.toString());
        } catch (IOException e) {
            return ToolHost.ToolResult.failure("Failed to read file: " + e.getMessage());
        }
    }

    private Path resolvePath(String filePath, ToolContext context) {
        Path p = Path.of(filePath);
        if (p.isAbsolute()) return p;
        return context.getWorkspaceRoot().resolve(filePath).normalize();
    }

    public static ToolDefinition toToolDefinition() {
        return ToolDefinition.builder()
                .name("read_file")
                .description("Read the contents of a file")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string", "description", "Path to the file"),
                                "offset", Map.of("type", "integer", "description", "Line offset to start reading"),
                                "limit", Map.of("type", "integer", "description", "Number of lines to read")
                        ),
                        "required", List.of("path")
                ))
                .parallelSafe(true)
                .executor(new FileReadTool())
                .build();
    }
}
