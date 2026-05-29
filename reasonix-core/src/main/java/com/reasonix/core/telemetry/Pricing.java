package com.reasonix.core.telemetry;

import com.reasonix.core.ports.ModelClient;

import java.util.Map;

public class Pricing {

    private static final Map<String, ModelPricing> PRICING_TABLE = Map.of(
            "deepseek-chat", new ModelPricing(0.0000014, 0.0000028, 0.00000014),
            "deepseek-reasoner", new ModelPricing(0.000004, 0.000016, 0.000001),
            "deepseek-v4-flash", new ModelPricing(0.0000007, 0.0000028, 0.00000007),
            "deepseek-v4-pro", new ModelPricing(0.0000084, 0.000028, 0.00000084)
    );

    public double calculateCost(ModelClient.Usage usage, String model) {
        ModelPricing pricing = PRICING_TABLE.getOrDefault(model, PRICING_TABLE.get("deepseek-chat"));

        double inputCost = usage.promptCacheMissTokens() * pricing.inputPerToken();
        double cacheCost = usage.promptCacheHitTokens() * pricing.cachePerToken();
        double outputCost = usage.completionTokens() * pricing.outputPerToken();

        return inputCost + cacheCost + outputCost;
    }

    public String formatCost(double costUsd) {
        if (costUsd < 0.05) return String.format("$%.4f", costUsd);
        if (costUsd < 0.20) return String.format("$%.3f", costUsd);
        return String.format("$%.2f", costUsd);
    }

    public CostColor costColor(double costUsd, boolean isSession) {
        double scale = isSession ? 10.0 : 1.0;
        if (costUsd < 0.05 * scale) return CostColor.GREEN;
        if (costUsd < 0.20 * scale) return CostColor.YELLOW;
        return CostColor.RED;
    }

    public enum CostColor { GREEN, YELLOW, RED }

    public record ModelPricing(double inputPerToken, double outputPerToken, double cachePerToken) {}
}
