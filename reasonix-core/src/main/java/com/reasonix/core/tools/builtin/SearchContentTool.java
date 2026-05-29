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
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SearchContentTool implements ToolExecutor {

    @Override
    public ToolHost.ToolResult execute(String toolName, Map<String, Object> arguments, ToolContext context) {
        String pattern = (String) arguments.get("pattern");
        if (pattern == null) return ToolHost.ToolResult.failure("No pattern provided");

        String path = (String) arguments.getOrDefault("path", ".");
        Path resolved = resolvePath(path, context);
        if (!Files.exists(resolved)) return ToolHost.ToolResult.failure("Path not found: " + resolved);

        try {
            Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            StringBuilder sb = new StringBuilder();
            int matchCount = 0;
            int maxMatches = 50;

            try (Stream<Path> walk = Files.walk(resolved)) {
                List<Path> files = walk.filter(Files::isRegularFile)
                        .filter(p -> !isBinaryFile(p))
                        .toList();

                for (Path file : files) {
                    if (matchCount >= maxMatches) break;
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        if (regex.matcher(lines.get(i)).find()) {
                            sb.append(file.getFileName()).append(":").append(i + 1).append(": ")
                                    .append(lines.get(i).trim()).append("\n");
                            matchCount++;
                            if (matchCount >= maxMatches) break;
                        }
                    }
                }
            }

            if (sb.isEmpty()) return ToolHost.ToolResult.success("No matches found");
            return ToolHost.ToolResult.success(sb.toString());
        } catch (IOException e) {
            return ToolHost.ToolResult.failure("Search failed: " + e.getMessage());
        }
    }

    private boolean isBinaryFile(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".class") || name.endsWith(".jar") || name.endsWith(".zip")
                || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".gif")
                || name.endsWith(".pdf") || name.endsWith(".exe") || name.endsWith(".so");
    }

    private Path resolvePath(String filePath, ToolContext context) {
        Path p = Path.of(filePath);
        if (p.isAbsolute()) return p;
        return context.getWorkspaceRoot().resolve(filePath).normalize();
    }

    public static ToolDefinition toToolDefinition() {
        return ToolDefinition.builder()
                .name("search_content")
                .description("Search for a pattern in file contents")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "pattern", Map.of("type", "string", "description", "Regex pattern to search"),
                                "path", Map.of("type", "string", "description", "Directory to search in")
                        ),
                        "required", List.of("pattern")
                ))
                .parallelSafe(true)
                .executor(new SearchContentTool())
                .build();
    }
}
