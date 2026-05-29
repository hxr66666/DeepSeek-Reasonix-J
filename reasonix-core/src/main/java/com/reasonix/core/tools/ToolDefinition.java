package com.reasonix.core.tools;

import com.reasonix.core.ports.ModelClient;

import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters,
        boolean parallelSafe,
        ToolExecutor executor
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private Map<String, Object> parameters = Map.of();
        private boolean parallelSafe = false;
        private ToolExecutor executor;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder parameters(Map<String, Object> parameters) { this.parameters = parameters; return this; }
        public Builder parallelSafe(boolean parallelSafe) { this.parallelSafe = parallelSafe; return this; }
        public Builder executor(ToolExecutor executor) { this.executor = executor; return this; }
        public ToolDefinition build() {
            return new ToolDefinition(name, description, parameters, parallelSafe, executor);
        }
    }

    public ModelClient.ToolSpec toToolSpec() {
        return new ModelClient.ToolSpec(name, description, parameters, parallelSafe);
    }
}
