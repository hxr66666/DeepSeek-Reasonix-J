package com.reasonix.core.skills;

import com.reasonix.core.ports.ModelClient;
import com.reasonix.core.ports.ToolHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class SkillRunner {

    private static final Logger log = LoggerFactory.getLogger(SkillRunner.class);

    private final ModelClient modelClient;
    private final ToolHost toolHost;

    public SkillRunner(ModelClient modelClient, ToolHost toolHost) {
        this.modelClient = modelClient;
        this.toolHost = toolHost;
    }

    public String runInline(SkillLoader.Skill skill, Map<String, Object> context) {
        String prompt = buildPrompt(skill, context);
        ModelClient.ChatRequest request = ModelClient.ChatRequest.builder()
                .model("deepseek-v4-flash")
                .systemPrompt(prompt)
                .messages(java.util.List.of())
                .build();

        ModelClient.ModelResponse response = modelClient.chat(request);
        return response.content();
    }

    public String runSubagent(SkillLoader.Skill skill, Map<String, Object> context) {
        String prompt = buildPrompt(skill, context);
        ModelClient.ChatRequest request = ModelClient.ChatRequest.builder()
                .model("deepseek-v4-flash")
                .systemPrompt(prompt)
                .messages(java.util.List.of(ModelClient.ChatMessage.user("Execute this skill")))
                .build();

        ModelClient.ModelResponse response = modelClient.chat(request);
        return response.content();
    }

    private String buildPrompt(SkillLoader.Skill skill, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are executing the skill: ").append(skill.name()).append("\n\n");
        sb.append("Skill instructions:\n").append(skill.content()).append("\n\n");

        if (context != null && !context.isEmpty()) {
            sb.append("Context:\n");
            context.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }

        return sb.toString();
    }
}
