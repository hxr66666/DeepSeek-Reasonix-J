package com.reasonix.core.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reasonix.core.ports.ModelClient;
import com.reasonix.core.ports.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class DuckDBSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(DuckDBSessionStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Connection conn;

    public DuckDBSessionStore(Connection conn) {
        this.conn = conn;
    }

    @Override
    public Session createSession(String workspaceRoot, String model) {
        String id = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO sessions (id, workspace_root, model, created_at, updated_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """;

        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, workspaceRoot);
            pstmt.setString(3, model);
            pstmt.execute();
            conn.commit();
            return getSession(id).orElseThrow();
        } catch (SQLException e) {
            log.error("Failed to create session: {}", e.getMessage());
            rollbackQuietly();
            throw new RuntimeException("Failed to create session", e);
        }
    }

    @Override
    public Optional<Session> getSession(String sessionId) {
        String sql = "SELECT * FROM sessions WHERE id = ?";
        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            var rs = pstmt.executeQuery();
            if (rs.next()) return Optional.of(parseSession(rs));
            return Optional.empty();
        } catch (SQLException e) {
            log.error("Failed to get session: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<Session> listSessions(String workspaceRoot) {
        String sql = "SELECT * FROM sessions WHERE workspace_root = ? ORDER BY updated_at DESC";
        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, workspaceRoot);
            return parseSessions(pstmt.executeQuery());
        } catch (SQLException e) {
            log.error("Failed to list sessions: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Session> listActiveSessions() {
        String sql = "SELECT * FROM sessions WHERE is_active = TRUE ORDER BY updated_at DESC";
        try (var pstmt = conn.prepareStatement(sql)) {
            return parseSessions(pstmt.executeQuery());
        } catch (SQLException e) {
            log.error("Failed to list active sessions: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void appendMessage(String sessionId, ModelClient.ChatMessage message) {
        String id = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO messages (id, session_id, role, content, reasoning, tool_calls, usage, model, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;

        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, sessionId);
            pstmt.setString(3, message.role());
            pstmt.setString(4, message.content());
            pstmt.setString(5, message.reasoning());
            pstmt.setString(6, message.toolCalls() != null ? MAPPER.writeValueAsString(message.toolCalls()) : null);
            pstmt.setString(7, null);
            pstmt.setString(8, null);
            pstmt.execute();
            conn.commit();
        } catch (Exception e) {
            log.error("Failed to append message: {}", e.getMessage());
            rollbackQuietly();
        }
    }

    @Override
    public void updateSessionCost(String sessionId, double costUsd) {
        String sql = "UPDATE sessions SET cost_usd = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, costUsd);
            pstmt.setString(2, sessionId);
            pstmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            log.error("Failed to update session cost: {}", e.getMessage());
            rollbackQuietly();
        }
    }

    @Override
    public void updateCacheHitRatio(String sessionId, double ratio) {
        String sql = "UPDATE sessions SET cache_hit_ratio = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, ratio);
            pstmt.setString(2, sessionId);
            pstmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            log.error("Failed to update cache hit ratio: {}", e.getMessage());
            rollbackQuietly();
        }
    }

    @Override
    public void incrementTurnCount(String sessionId) {
        String sql = "UPDATE sessions SET turn_count = turn_count + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            log.error("Failed to increment turn count: {}", e.getMessage());
            rollbackQuietly();
        }
    }

    @Override
    public void deactivateSession(String sessionId) {
        String sql = "UPDATE sessions SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sessionId);
            pstmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            log.error("Failed to deactivate session: {}", e.getMessage());
            rollbackQuietly();
        }
    }

    @Override
    public void deleteSession(String sessionId) {
        String deleteMessages = "DELETE FROM messages WHERE session_id = ?";
        String deleteSession = "DELETE FROM sessions WHERE id = ?";
        try (var pstmt1 = conn.prepareStatement(deleteMessages);
             var pstmt2 = conn.prepareStatement(deleteSession)) {
            pstmt1.setString(1, sessionId);
            pstmt1.execute();
            pstmt2.setString(1, sessionId);
            pstmt2.execute();
            conn.commit();
        } catch (SQLException e) {
            log.error("Failed to delete session: {}", e.getMessage());
            rollbackQuietly();
        }
    }

    private Session parseSession(ResultSet rs) throws SQLException {
        return new Session(
                rs.getString("id"),
                rs.getString("workspace_root"),
                rs.getString("model"),
                rs.getDouble("cost_usd"),
                rs.getDouble("cache_hit_ratio"),
                rs.getInt("turn_count"),
                rs.getBoolean("is_active"),
                rs.getTimestamp("created_at").getTime(),
                rs.getTimestamp("updated_at").getTime()
        );
    }

    private List<Session> parseSessions(ResultSet rs) throws SQLException {
        List<Session> result = new ArrayList<>();
        while (rs.next()) result.add(parseSession(rs));
        return result;
    }

    private void rollbackQuietly() {
        try { conn.rollback(); } catch (SQLException ignored) {}
    }
}
