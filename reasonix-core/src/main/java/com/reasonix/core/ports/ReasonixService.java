package com.reasonix.core.ports;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface ReasonixService {

    Flux<EventSink.LoopEvent> chatStream(String message, Path workspace);

    Mono<String> chat(String message, Path workspace);

    List<ToolInfo> listTools();

    Mono<ToolHost.ToolResult> executeTool(String name, Map<String, Object> arguments);

    List<SessionStore.Session> listSessions(String workspaceRoot);

    Mono<SessionStore.Session> restoreSession(String sessionId);

    record ToolInfo(
            String name,
            String description,
            Map<String, Object> parameters,
            boolean parallelSafe
    ) {}
}
