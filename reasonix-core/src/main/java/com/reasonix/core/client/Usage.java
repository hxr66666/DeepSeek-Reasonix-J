package com.reasonix.core.client;

public record Usage(
        long promptTokens,
        long completionTokens,
        long totalTokens,
        long promptCacheHitTokens,
        long promptCacheMissTokens,
        double costUsd
) {
    public static Usage empty() {
        return new Usage(0, 0, 0, 0, 0, 0.0);
    }

    public Usage add(Usage other) {
        return new Usage(
                promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                totalTokens + other.totalTokens,
                promptCacheHitTokens + other.promptCacheHitTokens,
                promptCacheMissTokens + other.promptCacheMissTokens,
                costUsd + other.costUsd
        );
    }

    public double cacheHitRatio() {
        long total = promptCacheHitTokens + promptCacheMissTokens;
        return total > 0 ? (double) promptCacheHitTokens / total : 0.0;
    }
}
