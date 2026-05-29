package com.reasonix.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final AtomicLong requestId = new AtomicLong(0);
    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private final List<ToolInfo> tools = new ArrayList<>();

    private Process stdioProcess;
    private BufferedWriter stdioWriter;
    private BufferedReader stdioReader;

    private WebSocket ws;
    private HttpClient httpClient;

    private final McpTransport transport;

    public McpClient(McpTransport transport) {
        this.transport = transport;
    }

    public void initialize() throws Exception {
        Map<String, Object> initParams = Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "DeepSeek-Reasonix", "version", "1.0.0")
        );

        JsonNode response = sendRequest("initialize", initParams);
        log.info("MCP server initialized: {}", response.path("serverInfo").path("name").asText("unknown"));

        sendNotification("notifications/initialized", Map.of());
        discoverTools();
    }

    public List<ToolInfo> listTools() {
        return Collections.unmodifiableList(tools);
    }

    public String callTool(String name, Map<String, Object> arguments) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        params.put("arguments", arguments != null ? arguments : Map.of());

        JsonNode response = sendRequest("tools/call", params);

        JsonNode content = response.path("content");
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : content) {
                sb.append(item.path("text").asText(""));
            }
            return sb.toString();
        }
        return response.toString();
    }

    private void discoverTools() throws Exception {
        JsonNode response = sendRequest("tools/list", Map.of());
        JsonNode toolsNode = response.path("tools");

        tools.clear();
        if (toolsNode.isArray()) {
            for (JsonNode tool : toolsNode) {
                tools.add(new ToolInfo(
                        tool.path("name").asText(),
                        tool.path("description").asText(""),
                        tool.path("inputSchema")
                ));
            }
        }
        log.info("Discovered {} MCP tools", tools.size());
    }

    private JsonNode sendRequest(String method, Map<String, Object> params) throws Exception {
        long id = requestId.incrementAndGet();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);

        String json = MAPPER.writeValueAsString(request);
        String responseJson = transport.sendAndReceive(json);
        JsonNode response = MAPPER.readTree(responseJson);

        if (response.has("error")) {
            String errorMsg = response.path("error").path("message").asText("Unknown error");
            throw new RuntimeException("MCP error: " + errorMsg);
        }

        return response.path("result");
    }

    private void sendNotification(String method, Map<String, Object> params) throws Exception {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.put("params", params);

        String json = MAPPER.writeValueAsString(notification);
        transport.send(json);
    }

    public void close() {
        try {
            transport.close();
        } catch (Exception e) {
            log.warn("Failed to close MCP transport", e);
        }
    }

    public record ToolInfo(String name, String description, JsonNode inputSchema) {}
}
