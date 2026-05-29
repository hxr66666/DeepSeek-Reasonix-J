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

public class ListDirectoryTool implements ToolExecutor {

    @Override
    public ToolHost.ToolResult execute(String toolName, Map<String, Object> arguments, ToolContext context) {
        String dirPath = (String) arguments.getOrDefault("path", ".");
        Path resolved = resolvePath(dirPath, context);

        if (!Files.exists(resolved)) return ToolHost.ToolResult.failure("Directory not found: " + resolved);
        if (!Files.isDirectory(resolved)) return ToolHost.ToolResult.failure("Not a directory: " + resolved);

        try (Stream<Path> stream = Files.list(resolved)) {
            StringBuilder sb = new StringBuilder();
            stream.sorted().forEach(p -> {
                try {
                    String type = Files.isDirectory(p) ? "DIR " : "FILE";
                    long size = Files.isRegularFile(p) ? Files.size(p) : 0;
                    sb.append(type).append(" ").append(p.getFileName());
                    if (Files.isRegularFile(p)) sb.append(" (").append(size).append(" bytes)");
                    sb.append("\n");
                } catch (IOException ignored) {}
            });
            return ToolHost.ToolResult.success(sb.toString());
        } catch (IOException e) {
            return ToolHost.ToolResult.failure("Failed to list directory: " + e.getMessage());
        }
    }

    private Path resolvePath(String filePath, ToolContext context) {
        Path p = Path.of(filePath);
        if (p.isAbsolute()) return p;
        return context.getWorkspaceRoot().resolve(filePath).normalize();
    }

    public static ToolDefinition toToolDefinition() {
        return ToolDefinition.builder()
                .name("list_directory")
                .description("List contents of a directory")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string", "description", "Directory path")
                        ),
                        "required", List.of()
                ))
                .parallelSafe(true)
                .executor(new ListDirectoryTool())
                .build();
    }
}
