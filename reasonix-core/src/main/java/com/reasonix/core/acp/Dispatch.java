package com.reasonix.core.acp;

import com.reasonix.core.ports.ModelClient;
import com.reasonix.core.ports.ToolHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class Dispatch {

    private static final Logger log = LoggerFactory.getLogger(Dispatch.class);

    private final ToolHost toolHost;
    private final ModelClient modelClient;

    public Dispatch(ToolHost toolHost, ModelClient modelClient) {
        this.toolHost = toolHost;
        this.modelClient = modelClient;
    }

    public ModelClient.ModelResponse dispatchAcpCall(String action, Map<String, Object> params) {
        log.info("ACP dispatch: action={}", action);
        return switch (action) {
            case "chat" -> handleChat(params);
            case "tool_call" -> handleToolCall(params);
            default -> {
                log.warn("Unknown ACP action: {}", action);
                yield new ModelClient.ModelResponse("Unknown action: " + action, List.of(), null,
                        ModelClient.Usage.empty(), null);
            }
        };
    }

    private ModelClient.ModelResponse handleChat(Map<String, Object> params) {
        String message = (String) params.getOrDefault("message", "");
        String model = (String) params.getOrDefault("model", "deepseek-v4-flash");

        ModelClient.ChatRequest request = ModelClient.ChatRequest.builder()
                .model(model)
                .messages(List.of(ModelClient.ChatMessage.user(message)))
                .build();

        return modelClient.chat(request);
    }

    private ModelClient.ModelResponse handleToolCall(Map<String, Object> params) {
        String toolName = (String) params.getOrDefault("tool", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

        var result = toolHost.dispatch(toolName, args);
        return new ModelClient.ModelResponse(result.content(), List.of(), null,
                ModelClient.Usage.empty(), null);
    }
}
