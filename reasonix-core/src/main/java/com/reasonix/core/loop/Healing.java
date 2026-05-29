package com.reasonix.core.loop;

import com.reasonix.common.util.JsonUtils;
import com.reasonix.core.ports.ModelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Healing {

    private static final Logger log = LoggerFactory.getLogger(Healing.class);

    private static final Pattern TOOL_CALL_IN_REASONING = Pattern.compile(
            "\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{[^}]*\\})\\s*}"
    );

    private static final Pattern UNBALANCED_JSON = Pattern.compile(
            "\\{[^}]*(?:\\{[^}]*\\}[^}]*)*"
    );

    public List<ModelClient.ToolCall> scavenge(String reasoningContent) {
        if (reasoningContent == null || reasoningContent.isBlank()) return List.of();

        List<ModelClient.ToolCall> found = new ArrayList<>();
        Matcher matcher = TOOL_CALL_IN_REASONING.matcher(reasoningContent);

        while (matcher.find()) {
            String name = matcher.group(1);
            String argsJson = matcher.group(2);
            try {
                Map<String, Object> args = JsonUtils.parseToMap(argsJson);
                found.add(new ModelClient.ToolCall(UUID.randomUUID().toString(), name, args));
            } catch (Exception e) {
                log.debug("Failed to parse scavenged tool call args: {}", argsJson);
            }
        }

        if (!found.isEmpty()) {
            log.info("Scavenged {} tool calls from reasoning content", found.size());
        }
        return found;
    }

    public String fixTruncation(String json) {
        if (json == null || json.isBlank()) return json;
        if (JsonUtils.isValidJson(json)) return json;

        int openBraces = countChar(json, '{');
        int closeBraces = countChar(json, '}');
        int openBrackets = countChar(json, '[');
        int closeBrackets = countChar(json, ']');

        StringBuilder fixed = new StringBuilder(json);

        while (closeBrackets < openBrackets) {
            fixed.append(']');
            closeBrackets++;
        }
        while (closeBraces < openBraces) {
            fixed.append('}');
            closeBraces++;
        }

        String result = fixed.toString();
        if (JsonUtils.isValidJson(result)) {
            log.info("Fixed truncated JSON by closing brackets");
            return result;
        }

        return json;
    }

    public List<ModelClient.ToolCall> suppressStorm(
            List<ModelClient.ToolCall> calls,
            List<StormWindowEntry> recentCalls,
            int windowSize) {

        if (recentCalls.isEmpty()) return calls;

        List<ModelClient.ToolCall> filtered = new ArrayList<>();
        for (var call : calls) {
            boolean isDuplicate = recentCalls.stream()
                    .skip(Math.max(0, recentCalls.size() - windowSize))
                    .anyMatch(entry ->
                            entry.toolName().equals(call.name()) &&
                                    entry.fingerprint().equals(fingerprint(call)));

            if (!isDuplicate) {
                filtered.add(call);
            } else {
                log.info("Suppressed duplicate tool call: {} (storm suppression)", call.name());
            }
        }

        return filtered;
    }

    private String fingerprint(ModelClient.ToolCall call) {
        return call.name() + ":" + JsonUtils.toJson(call.arguments());
    }

    private int countChar(String s, char c) {
        int count = 0;
        for (char ch : s.toCharArray()) {
            if (ch == c) count++;
        }
        return count;
    }

    public record StormWindowEntry(String toolName, String fingerprint, long timestamp) {}
}
