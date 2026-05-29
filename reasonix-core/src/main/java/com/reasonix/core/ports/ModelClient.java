package com.reasonix.core.ports;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public interface ModelClient {

    Flux<ModelStreamChunk> chatStream(ChatRequest request);

    ModelResponse chat(ChatRequest request);

    record ChatRequest(
            String model,
            String systemPrompt,
            List<ChatMessage> messages,
            List<ToolSpec> toolSpecs,
            Double temperature,
            Integer maxTokens
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String model;
            private String systemPrompt;
            private List<ChatMessage> messages = List.of();
            private List<ToolSpec> toolSpecs = List.of();
            private Double temperature;
            private Integer maxTokens;

            public Builder model(String model) { this.model = model; return this; }
            public Builder systemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; return this; }
            public Builder messages(List<ChatMessage> messages) { this.messages = messages; return this; }
            public Builder toolSpecs(List<ToolSpec> toolSpecs) { this.toolSpecs = toolSpecs; return this; }
            public Builder temperature(Double temperature) { this.temperature = temperature; return this; }
            public Builder maxTokens(Integer maxTokens) { this.maxTokens = maxTokens; return this; }
            public ChatRequest build() {
                return new ChatRequest(model, systemPrompt, messages, toolSpecs, temperature, maxTokens);
            }
        }
    }

    record ChatMessage(String role, String content, List<ToolCall> toolCalls, String reasoning) {
        public static ChatMessage user(String content) {
            return new ChatMessage("user", content, null, null);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content, null, null);
        }

        public static ChatMessage assistantWithTools(List<ToolCall> toolCalls) {
            return new ChatMessage("assistant", null, toolCalls, null);
        }

        public static ChatMessage toolResult(String toolCallId, String content) {
            return new ChatMessage("tool", content, null, null);
        }

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content, null, null);
        }
    }

    record ToolCall(String id, String name, Map<String, Object> arguments) {}

    record ToolSpec(
            String name,
            String description,
            Map<String, Object> parameters,
            boolean parallelSafe
    ) {}

    record ModelStreamChunk(
            String token,
            ToolCall toolCall,
            String reasoning,
            Usage usage
    ) {}

    record ModelResponse(
            String content,
            List<ToolCall> toolCalls,
            String reasoning,
            Usage usage,
            String model
    ) {}

    record Usage(
            long promptTokens,
            long completionTokens,
            long totalTokens,
            long promptCacheHitTokens,
            long promptCacheMissTokens
    ) {
        public static Usage empty() {
            return new Usage(0, 0, 0, 0, 0);
        }
    }
}
