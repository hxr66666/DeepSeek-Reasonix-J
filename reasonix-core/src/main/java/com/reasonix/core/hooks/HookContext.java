package com.reasonix.core.hooks;

public record HookContext(
        String toolName,
        String sessionId,
        String workspaceRoot
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String toolName;
        private String sessionId;
        private String workspaceRoot;

        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder workspaceRoot(String workspaceRoot) { this.workspaceRoot = workspaceRoot; return this; }
        public HookContext build() { return new HookContext(toolName, sessionId, workspaceRoot); }
    }
}
