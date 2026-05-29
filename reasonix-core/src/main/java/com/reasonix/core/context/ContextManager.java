package com.reasonix.core.context;

import com.reasonix.core.ports.ModelClient;

import java.util.Map;

public class ContextManager {

    public static final double HISTORY_FOLD_THRESHOLD = 0.75;
    public static final double HISTORY_FOLD_TAIL_FRACTION = 0.2;
    public static final double AGGRESSIVE_THRESHOLD = 0.78;
    public static final double AGGRESSIVE_TAIL_FRACTION = 0.1;
    public static final double FORCE_SUMMARY_THRESHOLD = 0.8;

    private static final Map<String, Long> CONTEXT_TOKENS = Map.of(
            "deepseek-chat", 131072L,
            "deepseek-reasoner", 131072L,
            "deepseek-v4-flash", 131072L,
            "deepseek-v4-pro", 131072L
    );

    private boolean alreadyFoldedThisTurn = false;

    public PostUsageDecision decideAfterUsage(ModelClient.Usage usage, String model) {
        long ctxMax = CONTEXT_TOKENS.getOrDefault(model, 131072L);
        double ratio = (double) usage.promptTokens() / ctxMax;

        if (ratio > FORCE_SUMMARY_THRESHOLD) {
            return new PostUsageDecision.ExitWithSummary(ratio);
        }
        if (alreadyFoldedThisTurn) {
            return new PostUsageDecision.NoOp();
        }
        if (ratio > AGGRESSIVE_THRESHOLD) {
            alreadyFoldedThisTurn = true;
            return new PostUsageDecision.Fold(ratio, AGGRESSIVE_TAIL_FRACTION, true);
        }
        if (ratio > HISTORY_FOLD_THRESHOLD) {
            alreadyFoldedThisTurn = true;
            return new PostUsageDecision.Fold(ratio, HISTORY_FOLD_TAIL_FRACTION, false);
        }
        return new PostUsageDecision.NoOp();
    }

    public void resetTurnState() {
        alreadyFoldedThisTurn = false;
    }

    public sealed interface PostUsageDecision {
        record NoOp() implements PostUsageDecision {}
        record Fold(double ratio, double tailFraction, boolean aggressive) implements PostUsageDecision {}
        record ExitWithSummary(double ratio) implements PostUsageDecision {}
    }
}
