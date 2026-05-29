package com.reasonix.core.tools.builtin;

import com.reasonix.core.ports.ToolHost;
import com.reasonix.core.tools.ToolContext;
import com.reasonix.core.tools.ToolDefinition;
import com.reasonix.core.tools.ToolExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

public class FileWriteTool implements ToolExecutor {

    @Override
    public ToolHost.ToolResult execute(String toolName, Map<String, Object> arguments, ToolContext context) {
        String filePath = (String) arguments.get("path");
        String content = (String) arguments.get("content");
        if (filePath == null) return ToolHost.ToolResult.failure("No path provided");
        if (content == null) content = "";

        Path resolved = resolvePath(filePath, context);
        try {
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return ToolHost.ToolResult.success("File written: " + resolved);
        } catch (IOException e) {
            return ToolHost.ToolResult.failure("Failed to write file: " + e.getMessage());
        }
    }

    private Path resolvePath(String filePath, ToolContext context) {
        Path p = Path.of(filePath);
        if (p.isAbsolute()) return p;
        return context.getWorkspaceRoot().resolve(filePath).normalize();
    }

    public static ToolDefinition toToolDefinition() {
        return ToolDefinition.builder()
                .name("write_file")
                .description("Write content to a file")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string", "description", "Path to the file"),
                                "content", Map.of("type", "string", "description", "Content to write")
                        ),
                        "required", List.of("path", "content")
                ))
                .parallelSafe(false)
                .executor(new FileWriteTool())
                .build();
    }
}
