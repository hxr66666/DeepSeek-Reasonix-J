package com.reasonix.core.client;

import com.reasonix.core.ports.ModelClient;
import com.reasonix.core.ports.ModelClient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Map;

public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private static final Map<String, Long> MODEL_CONTEXT = Map.of(
            "deepseek-chat", 131072L,
            "deepseek-reasoner", 131072L,
            "deepseek-v4-flash", 131072L,
            "deepseek-v4-pro", 131072L
    );

    private static final Map<String, Double> MODEL_PRICING = Map.of(
            "deepseek-chat", 0.0000014,
            "deepseek-reasoner", 0.000004,
            "deepseek-v4-flash", 0.0000007,
            "deepseek-v4-pro", 0.0000084
    );

    private final ModelClient modelClient;
    private String currentModel;

    public DeepSeekClient(ModelClient modelClient, String defaultModel) {
        this.modelClient = modelClient;
        this.currentModel = defaultModel;
    }

    public Flux<ModelStreamChunk> chatStream(String systemPrompt, java.util.List<ChatMessage> messages) {
        ChatRequest request = ChatRequest.builder()
                .model(currentModel)
                .systemPrompt(systemPrompt)
                .messages(messages)
                .build();
        return modelClient.chatStream(request);
    }

    public ModelResponse chat(String systemPrompt, java.util.List<ChatMessage> messages) {
        ChatRequest request = ChatRequest.builder()
                .model(currentModel)
                .systemPrompt(systemPrompt)
                .messages(messages)
                .build();
        return modelClient.chat(request);
    }

    public void setModel(String model) {
        this.currentModel = model;
    }

    public String getModel() {
        return currentModel;
    }

    public long getContextLimit() {
        return MODEL_CONTEXT.getOrDefault(currentModel, 131072L);
    }

    public double calculateCost(Usage usage) {
        Double pricePerToken = MODEL_PRICING.getOrDefault(currentModel, 0.0000014);
        return usage.promptTokens() * pricePerToken + usage.completionTokens() * pricePerToken * 2;
    }
}
