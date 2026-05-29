package com.reasonix.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reasonix.core.ports.ModelClient;
import com.reasonix.core.ports.SessionStore;
import com.reasonix.core.ports.MemoryStore;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class RestApi {

    private static final Logger log = LoggerFactory.getLogger(RestApi.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Javalin app;
    private final SessionStore sessionStore;
    private final MemoryStore memoryStore;
    private final ModelClient modelClient;

    public RestApi(SessionStore sessionStore, MemoryStore memoryStore, ModelClient modelClient, int port) {
        this.sessionStore = sessionStore;
        this.memoryStore = memoryStore;
        this.modelClient = modelClient;

        this.app = Javalin.create(config -> {
            config.http.maxRequestSize = 10_000_000L;
        });

        registerRoutes();
    }

    private void registerRoutes() {
        app.get("/api/health", this::healthCheck);

        app.post("/api/sessions", this::createSession);
        app.get("/api/sessions", this::listSessions);
        app.get("/api/sessions/{id}", this::getSession);
        app.delete("/api/sessions/{id}", this::deleteSession);

        app.post("/api/chat", this::chat);
        app.post("/api/chat/stream", this::chatStream);

        app.post("/api/memories", this::createMemory);
        app.get("/api/memories", this::listMemories);
        app.delete("/api/memories/{id}", this::deleteMemory);
        app.post("/api/memories/{id}/pin", this::pinMemory);
        app.post("/api/memories/{id}/unpin", this::unpinMemory);
    }

    private void healthCheck(Context ctx) {
        ctx.json(Map.of("status", "ok", "version", "1.0.0"));
    }

    private void createSession(Context ctx) throws Exception {
        Map<String, Object> body = MAPPER.readValue(ctx.body(), Map.class);
        String workspace = (String) body.getOrDefault("workspace", ".");
        String model = (String) body.getOrDefault("model", "deepseek-v4-flash");

        var session = sessionStore.createSession(workspace, model);
        ctx.status(201).json(session);
    }

    private void listSessions(Context ctx) {
        String workspace = ctx.queryParam("workspace");
        var sessions = workspace != null
                ? sessionStore.listSessions(workspace)
                : sessionStore.listActiveSessions();
        ctx.json(sessions);
    }

    private void getSession(Context ctx) {
        String id = ctx.pathParam("id");
        sessionStore.getSession(id)
                .ifPresentOrElse(ctx::json, () -> ctx.status(404).json(Map.of("error", "Session not found")));
    }

    private void deleteSession(Context ctx) {
        String id = ctx.pathParam("id");
        sessionStore.deleteSession(id);
        ctx.status(204);
    }

    private void chat(Context ctx) throws Exception {
        Map<String, Object> body = MAPPER.readValue(ctx.body(), Map.class);
        String message = (String) body.get("message");
        String model = (String) body.getOrDefault("model", "deepseek-v4-flash");

        ModelClient.ChatRequest request = ModelClient.ChatRequest.builder()
                .model(model)
                .messages(java.util.List.of(ModelClient.ChatMessage.user(message)))
                .build();

        ModelClient.ModelResponse response = modelClient.chat(request);
        ctx.json(Map.of("content", response.content(), "usage", response.usage()));
    }

    private void chatStream(Context ctx) {
        ctx.contentType("text/event-stream");

        // SSE streaming placeholder - actual implementation uses WebSocket
        ctx.result("Streaming via WebSocket at /ws/chat");
    }

    private void createMemory(Context ctx) throws Exception {
        Map<String, Object> body = MAPPER.readValue(ctx.body(), Map.class);
        String content = (String) body.get("content");
        String typeStr = (String) body.getOrDefault("type", "note");
        MemoryStore.MemoryType type = MemoryStore.MemoryType.valueOf(typeStr.toUpperCase());

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) body.getOrDefault("metadata", Map.of());

        memoryStore.remember(content, type, metadata);
        ctx.status(201).json(Map.of("status", "created"));
    }

    private void listMemories(Context ctx) {
        String query = ctx.queryParam("query");
        String typeStr = ctx.queryParam("type");
        int limit = ctx.queryParam("limit") != null ? Integer.parseInt(ctx.queryParam("limit")) : 20;

        MemoryStore.MemoryType type = typeStr != null
                ? MemoryStore.MemoryType.valueOf(typeStr.toUpperCase()) : null;

        var memories = memoryStore.recall(query, type, limit);
        ctx.json(memories);
    }

    private void deleteMemory(Context ctx) {
        String id = ctx.pathParam("id");
        boolean deleted = memoryStore.forget(id);
        ctx.status(deleted ? 204 : 404);
    }

    private void pinMemory(Context ctx) {
        String id = ctx.pathParam("id");
        memoryStore.pin(id);
        ctx.json(Map.of("status", "pinned"));
    }

    private void unpinMemory(Context ctx) {
        String id = ctx.pathParam("id");
        memoryStore.unpin(id);
        ctx.json(Map.of("status", "unpinned"));
    }

    public void start() {
        app.start(0);
        log.info("REST API started on port {}", app.port());
    }

    public void start(int port) {
        app.start(port);
        log.info("REST API started on port {}", port);
    }

    public void stop() {
        app.stop();
    }

    public int getPort() {
        return app.port();
    }
}
