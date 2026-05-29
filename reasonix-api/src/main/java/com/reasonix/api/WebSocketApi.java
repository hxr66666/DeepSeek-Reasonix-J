package com.reasonix.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reasonix.core.ports.EventSink;
import com.reasonix.core.ports.ModelClient;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketApi {

    private static final Logger log = LoggerFactory.getLogger(WebSocketApi.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Javalin app;
    private final Map<String, WsContext> connections = new ConcurrentHashMap<>();
    private final EventSink eventSink;

    public WebSocketApi(EventSink eventSink, int port) {
        this.eventSink = eventSink;

        this.app = Javalin.create();
        registerWebSocket();
    }

    private void registerWebSocket() {
        app.ws("/ws/chat", ws -> {
            ws.onConnect(ctx -> {
                String sessionId = ctx.queryParam("session");
                if (sessionId == null) sessionId = "default";
                connections.put(sessionId, ctx);
                log.info("WebSocket connected: session={}", sessionId);
            });

            ws.onClose(ctx -> {
                String sessionId = ctx.queryParam("session");
                if (sessionId == null) sessionId = "default";
                connections.remove(sessionId);
                log.info("WebSocket disconnected: session={}", sessionId);
            });

            ws.onMessage(ctx -> {
                try {
                    Map<String, Object> message = MAPPER.readValue(ctx.message(), Map.class);
                    String type = (String) message.getOrDefault("type", "chat");
                    handleIncomingMessage(ctx, type, message);
                } catch (Exception e) {
                    log.error("Failed to parse WebSocket message", e);
                }
            });

            ws.onError(ctx -> {
                log.error("WebSocket error: {}", ctx.error());
            });
        });
    }

    private void handleIncomingMessage(WsContext ctx, String type, Map<String, Object> message) {
        switch (type) {
            case "chat" -> handleChatMessage(ctx, message);
            case "interrupt" -> handleInterrupt(ctx, message);
            default -> ctx.send("{\"type\":\"error\",\"message\":\"Unknown message type: " + type + "\"}");
        }
    }

    private void handleChatMessage(WsContext ctx, Map<String, Object> message) {
        String content = (String) message.get("content");
        String model = (String) message.getOrDefault("model", "deepseek-v4-flash");

        // Forward to core loop - actual implementation connects to CacheFirstLoop
        ctx.send("{\"type\":\"ack\",\"message\":\"Message received\"}");
    }

    private void handleInterrupt(WsContext ctx, Map<String, Object> message) {
        ctx.send("{\"type\":\"interrupted\",\"message\":\"Processing interrupted\"}");
    }

    public void broadcast(String sessionId, String type, Object data) {
        WsContext ctx = connections.get(sessionId);
        if (ctx != null) {
            try {
                Map<String, Object> message = Map.of("type", type, "data", data);
                ctx.send(MAPPER.writeValueAsString(message));
            } catch (Exception e) {
                log.error("Failed to broadcast to session {}", sessionId, e);
            }
        }
    }

    public void broadcastAll(String type, Object data) {
        for (var entry : connections.entrySet()) {
            broadcast(entry.getKey(), type, data);
        }
    }

    public void start(int port) {
        app.start(port);
        log.info("WebSocket API started on port {}", port);
    }

    public void stop() {
        app.stop();
    }
}
