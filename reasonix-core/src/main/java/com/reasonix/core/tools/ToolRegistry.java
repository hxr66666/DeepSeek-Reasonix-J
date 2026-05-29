package com.reasonix.core.tools;

import com.reasonix.core.ports.ModelClient;
import com.reasonix.core.ports.ToolHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry implements ToolHost {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private ToolContext defaultContext;

    public void register(ToolDefinition tool) {
        if (tools.containsKey(tool.name())) {
            log.warn("Tool '{}' already registered, replacing", tool.name());
        }
        tools.put(tool.name(), tool);
    }

    public void unregister(String toolName) {
        tools.remove(toolName);
    }

    @Override
    public ToolHost.ToolResult dispatch(String toolName, Map<String, Object> arguments) {
        ToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            return ToolHost.ToolResult.failure("Unknown tool: " + toolName);
        }

        try {
            Map<String, Object> resolvedArgs = flattenIfNeeded(tool, arguments);
            return tool.executor().execute(toolName, resolvedArgs, defaultContext);
        } catch (Exception e) {
            log.error("Tool '{}' execution failed", toolName, e);
            return ToolHost.ToolResult.failure("Tool '" + toolName + "' failed: " + e.getMessage());
        }
    }

    @Override
    public boolean hasTool(String toolName) {
        return tools.containsKey(toolName);
    }

    @Override
    public List<String> listToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    public List<ModelClient.ToolSpec> getToolSpecs() {
        return tools.values().stream()
                .map(ToolDefinition::toToolSpec)
                .toList();
    }

    public Optional<ToolDefinition> getTool(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public void setDefaultContext(ToolContext context) {
        this.defaultContext = context;
    }

    private Map<String, Object> flattenIfNeeded(ToolDefinition tool, Map<String, Object> arguments) {
        if (arguments == null) return Map.of();

        int leafCount = countLeaves(tool.parameters());
        int depth = maxDepth(tool.parameters());

        if (leafCount > 10 || depth > 2) {
            return renestArguments(arguments);
        }
        return arguments;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> renestArguments(Map<String, Object> flat) {
        Map<String, Object> nested = new HashMap<>();
        for (var entry : flat.entrySet()) {
            String key = entry.getKey();
            if (key.contains(".")) {
                String[] parts = key.split("\\.");
                Map<String, Object> current = nested;
                for (int i = 0; i < parts.length - 1; i++) {
                    current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new HashMap<>());
                }
                current.put(parts[parts.length - 1], entry.getValue());
            } else {
                nested.put(key, entry.getValue());
            }
        }
        return nested;
    }

    @SuppressWarnings("unchecked")
    private int countLeaves(Map<String, Object> schema) {
        if (schema == null) return 0;
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null) return 0;
        int count = 0;
        for (var value : properties.values()) {
            if (value instanceof Map<?, ?> m) {
                if ("object".equals(m.get("type"))) {
                    count += countLeaves((Map<String, Object>) m);
                } else {
                    count++;
                }
            } else {
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private int maxDepth(Map<String, Object> schema) {
        if (schema == null) return 0;
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null) return 0;
        int max = 1;
        for (var value : properties.values()) {
            if (value instanceof Map<?, ?> m && "object".equals(m.get("type"))) {
                max = Math.max(max, 1 + maxDepth((Map<String, Object>) m));
            }
        }
        return max;
    }
}
