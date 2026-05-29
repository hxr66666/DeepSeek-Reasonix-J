package com.reasonix.core.ports;

import java.util.Map;

public interface ToolHost {

    ToolResult dispatch(String toolName, Map<String, Object> arguments);

    boolean hasTool(String toolName);

    java.util.List<String> listToolNames();

    record ToolResult(
            String toolCallId,
            String content,
            boolean isError,
            Map<String, Object> metadata
    ) {
        public static ToolResult success(String content) {
            return new ToolResult(null, content, false, Map.of());
        }

        public static ToolResult success(String toolCallId, String content) {
            return new ToolResult(toolCallId, content, false, Map.of());
        }

        public static ToolResult failure(String errorMessage) {
            return new ToolResult(null, errorMessage, true, Map.of());
        }

        public static ToolResult failure(String toolCallId, String errorMessage) {
            return new ToolResult(toolCallId, errorMessage, true, Map.of());
        }
    }
}
