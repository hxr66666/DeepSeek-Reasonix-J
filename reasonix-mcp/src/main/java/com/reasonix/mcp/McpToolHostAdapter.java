package com.reasonix.mcp;

import com.reasonix.core.ports.ModelClient;
import com.reasonix.core.ports.ToolHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class McpToolHostAdapter implements ToolHost {

    private static final Logger log = LoggerFactory.getLogger(McpToolHostAdapter.class);

    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();
    private final ToolHost localTools;

    public McpToolHostAdapter(ToolHost localTools) {
        this.localTools = localTools;
    }

    public void registerClient(String serverName, McpClient client) {
        clients.put(serverName, client);
        log.info("Registered MCP client: {} ({} tools)", serverName, client.listTools().size());
    }

    public void unregisterClient(String serverName) {
        McpClient removed = clients.remove(serverName);
        if (removed != null) {
            removed.close();
            log.info("Unregistered MCP client: {}", serverName);
        }
    }

    @Override
    public ToolResult dispatch(String toolName, Map<String, Object> arguments) {
        if (localTools.hasTool(toolName)) {
            return localTools.dispatch(toolName, arguments);
        }

        for (var entry : clients.entrySet()) {
            McpClient client = entry.getValue();
            for (var tool : client.listTools()) {
                if (tool.name().equals(toolName)) {
                    try {
                        String result = client.callTool(toolName, arguments);
                        return ToolResult.success(result);
                    } catch (Exception e) {
                        return ToolResult.failure("MCP tool '" + toolName + "' failed: " + e.getMessage());
                    }
                }
            }
        }

        return ToolResult.failure("Unknown tool: " + toolName);
    }

    @Override
    public boolean hasTool(String toolName) {
        if (localTools.hasTool(toolName)) return true;
        return clients.values().stream()
                .anyMatch(c -> c.listTools().stream().anyMatch(t -> t.name().equals(toolName)));
    }

    @Override
    public List<String> listToolNames() {
        List<String> names = new ArrayList<>(localTools.listToolNames());
        clients.values().forEach(c -> c.listTools().forEach(t -> names.add(t.name())));
        return names;
    }

    public List<ModelClient.ToolSpec> getAllToolSpecs() {
        List<ModelClient.ToolSpec> specs = new ArrayList<>();

        if (localTools instanceof com.reasonix.core.tools.ToolRegistry registry) {
            specs.addAll(registry.getToolSpecs());
        }

        for (var client : clients.values()) {
            for (var tool : client.listTools()) {
                specs.add(new ModelClient.ToolSpec(
                        tool.name(),
                        tool.description(),
                        convertSchema(tool.inputSchema()),
                        true
                ));
            }
        }

        return specs;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertSchema(com.fasterxml.jackson.databind.JsonNode schema) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .treeToValue(schema, Map.class);
        } catch (Exception e) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }
}
