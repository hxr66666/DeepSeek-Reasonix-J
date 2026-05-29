package com.reasonix.core.acp;

public record Protocol(
        String version,
        String sessionId,
        String workspaceRoot
) {
    public static final String CURRENT_VERSION = "1.0";

    public static Protocol create(String sessionId, String workspaceRoot) {
        return new Protocol(CURRENT_VERSION, sessionId, workspaceRoot);
    }
}
