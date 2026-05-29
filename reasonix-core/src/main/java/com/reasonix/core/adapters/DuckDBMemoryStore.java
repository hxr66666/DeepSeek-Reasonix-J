package com.reasonix.core.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reasonix.core.ports.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

public class DuckDBMemoryStore implements MemoryStore {

    private static final Logger log = LoggerFactory.getLogger(DuckDBMemoryStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Connection conn;

    public DuckDBMemoryStore(Connection conn) {
        this.conn = conn;
    }

    @Override
    public void remember(String content, MemoryType type, Map<String, Object> metadata) {
        String id = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO memories (id, type, content, metadata, workspace, created_at, accessed_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """;

        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.setString(2, type.name().toLowerCase());
            pstmt.setString(3, content);
            pstmt.setString(4, metadata != null ? MAPPER.writeValueAsString(metadata) : null);
            pstmt.setString(5, metadata != null ? (String) metadata.getOrDefault("workspace", "") : "");
            pstmt.execute();
            conn.commit();
        } catch (Exception e) {
            log.error("Failed to remember: {}", e.getMessage());
            rollbackQuietly();
        }
    }

    @Override
    public List<Memory> recall(String query, MemoryType type, int limit) {
        String sql = """
            SELECT * FROM memories
            WHERE (? IS NULL OR type = ?)
              AND (? IS NULL OR content LIKE ?)
            ORDER BY
                CASE WHEN is_pinned THEN 1 ELSE 2 END,
                accessed_at DESC
            LIMIT ?
        """;

        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, type != null ? type.name().toLowerCase() : null);
            pstmt.setString(2, type != null ? type.name().toLowerCase() : null);
            pstmt.setString(3, query);
            pstmt.setString(4, query != null ? "%" + query + "%" : null);
            pstmt.setInt(5, limit);

            var rs = pstmt.executeQuery();
            return parseMemories(rs);
        } catch (SQLException e) {
            log.error("Failed to recall memories: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<Memory> getById(String id) {
        String sql = "SELECT * FROM memories WHERE id = ?";
        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            var rs = pstmt.executeQuery();
            var results = parseMemories(rs);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (SQLException e) {
            log.error("Failed to get memory by id: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean forget(String id) {
        String sql = "DELETE FROM memories WHERE id = ?";
        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            int rows = pstmt.executeUpdate();
            conn.commit();
            return rows > 0;
        } catch (SQLException e) {
            log.error("Failed to forget memory: {}", e.getMessage());
            rollbackQuietly();
            return false;
        }
    }

    @Override
    public List<Memory> listByWorkspace(String workspace, MemoryType type, int limit) {
        String sql = """
            SELECT * FROM memories
            WHERE workspace = ? AND (type = ? OR ? IS NULL)
            ORDER BY CASE WHEN is_pinned THEN 1 ELSE 2 END, accessed_at DESC
            LIMIT ?
        """;

        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, workspace);
            pstmt.setString(2, type != null ? type.name().toLowerCase() : null);
            pstmt.setString(3, type != null ? type.name().toLowerCase() : null);
            pstmt.setInt(4, limit);
            return parseMemories(pstmt.executeQuery());
        } catch (SQLException e) {
            log.error("Failed to list memories by workspace: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void pin(String id) {
        updatePinned(id, true);
    }

    @Override
    public void unpin(String id) {
        updatePinned(id, false);
    }

    private void updatePinned(String id, boolean pinned) {
        String sql = "UPDATE memories SET is_pinned = ? WHERE id = ?";
        try (var pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, pinned);
            pstmt.setString(2, id);
            pstmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            log.error("Failed to update pin status: {}", e.getMessage());
            rollbackQuietly();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Memory> parseMemories(ResultSet rs) throws SQLException {
        List<Memory> result = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> metadata = null;
            String metadataJson = rs.getString("metadata");
            if (metadataJson != null) {
                try {
                    metadata = MAPPER.readValue(metadataJson, Map.class);
                } catch (Exception e) {
                    metadata = Map.of();
                }
            }

            result.add(new Memory(
                    rs.getString("id"),
                    MemoryType.valueOf(rs.getString("type").toUpperCase()),
                    rs.getString("content"),
                    metadata != null ? metadata : Map.of(),
                    rs.getString("workspace"),
                    rs.getBoolean("is_pinned"),
                    rs.getTimestamp("created_at").getTime(),
                    rs.getTimestamp("accessed_at").getTime()
            ));
        }
        return result;
    }

    private void rollbackQuietly() {
        try { conn.rollback(); } catch (SQLException ignored) {}
    }
}
