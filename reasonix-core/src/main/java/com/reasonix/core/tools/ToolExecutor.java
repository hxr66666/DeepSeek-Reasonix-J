package com.reasonix.core.tools;

import com.reasonix.core.ports.ToolHost;

import java.util.Map;

@FunctionalInterface
public interface ToolExecutor {
    ToolHost.ToolResult execute(String toolName, Map<String, Object> arguments, ToolContext context);
}
