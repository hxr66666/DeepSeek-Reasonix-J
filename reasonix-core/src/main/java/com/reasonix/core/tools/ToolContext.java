package com.reasonix.core.tools;

import java.nio.file.Path;
import java.util.Map;

public class ToolContext {

    private final Path workspaceRoot;
    private final Map<String, Object> config;
    private final String sessionId;

    public ToolContext(Path workspaceRoot, Map<String, Object> config, String sessionId) {
        this.workspaceRoot = workspaceRoot;
        this.config = config;
        this.sessionId = sessionId;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public String getSessionId() {
        return sessionId;
    }

    public <T> T getConfig(String key, Class<T> type, T defaultValue) {
        Object value = config.get(key);
        if (value == null) return defaultValue;
        if (type.isInstance(value)) return type.cast(value);
        return defaultValue;
    }
}
