package com.reasonix.core.ports;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MemoryStore {

    void remember(String content, MemoryType type, Map<String, Object> metadata);

    List<Memory> recall(String query, MemoryType type, int limit);

    Optional<Memory> getById(String id);

    boolean forget(String id);

    List<Memory> listByWorkspace(String workspace, MemoryType type, int limit);

    void pin(String id);

    void unpin(String id);

    enum MemoryType {
        user, feedback, project, reference
    }

    record Memory(
            String id,
            MemoryType type,
            String content,
            Map<String, Object> metadata,
            String workspace,
            boolean pinned,
            long createdAt,
            long accessedAt
    ) {}
}
