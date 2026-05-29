package com.reasonix.core.ports;

import java.util.List;
import java.util.Optional;

public interface SessionStore {

    Session createSession(String workspaceRoot, String model);

    Optional<Session> getSession(String sessionId);

    List<Session> listSessions(String workspaceRoot);

    List<Session> listActiveSessions();

    void appendMessage(String sessionId, ModelClient.ChatMessage message);

    void updateSessionCost(String sessionId, double costUsd);

    void updateCacheHitRatio(String sessionId, double ratio);

    void incrementTurnCount(String sessionId);

    void deactivateSession(String sessionId);

    void deleteSession(String sessionId);

    record Session(
            String id,
            String workspaceRoot,
            String model,
            double costUsd,
            double cacheHitRatio,
            int turnCount,
            boolean isActive,
            long createdAt,
            long updatedAt
    ) {}
}
