package com.reasonix.core.loop;

import com.reasonix.core.ports.ModelClient;
import com.reasonix.core.ports.ModelClient.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Shrink {

    private static final Logger log = LoggerFactory.getLogger(Shrink.class);

    public static final int TURN_END_RESULT_CAP_TOKENS = 3000;

    public List<ChatMessage> shrinkToolResults(List<ChatMessage> messages, int capTokens) {
        List<ChatMessage> result = new ArrayList<>();

        for (ChatMessage msg : messages) {
            if ("tool".equals(msg.role()) && msg.content() != null) {
                int estimatedTokens = estimateTokens(msg.content());
                if (estimatedTokens > capTokens) {
                    String shrunk = shrinkContent(msg.content(), capTokens);
                    result.add(new ChatMessage(msg.role(), shrunk, msg.toolCalls(), msg.reasoning()));
                } else {
                    result.add(msg);
                }
            } else {
                result.add(msg);
            }
        }

        return result;
    }

    public List<ChatMessage> foldHistory(List<ChatMessage> messages, double tailFraction) {
        if (messages.isEmpty()) return messages;

        int total = messages.size();
        int keepCount = Math.max(1, (int) (total * tailFraction));
        int removeCount = total - keepCount;

        if (removeCount <= 0) return messages;

        List<ChatMessage> toRemove = messages.subList(0, removeCount);
        String summary = summarizeMessages(toRemove);

        List<ChatMessage> folded = new ArrayList<>();
        folded.add(ChatMessage.system("[Context folded] Previous conversation summary:\n" + summary));
        folded.addAll(messages.subList(removeCount, total));

        log.info("Folded {} messages into summary, keeping {} recent messages", removeCount, keepCount);
        return folded;
    }

    private String shrinkContent(String content, int targetTokens) {
        int targetChars = targetTokens * 4;
        if (content.length() <= targetChars) return content;

        String truncated = content.substring(0, targetChars);
        return truncated + "\n\n[... content truncated, original length: " + content.length() + " chars ...]";
    }

    private String summarizeMessages(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            sb.append("[").append(msg.role()).append("] ");
            if (msg.content() != null) {
                String content = msg.content();
                if (content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }
                sb.append(content);
            }
            if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                sb.append(" [called tools: ");
                for (var tc : msg.toolCalls()) {
                    sb.append(tc.name()).append(" ");
                }
                sb.append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        return text.length() / 4;
    }
}
