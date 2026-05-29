package com.reasonix.core.core;

import com.reasonix.core.ports.EventSink;
import com.reasonix.core.ports.ModelClient;

import java.util.List;

public class Reducers {

    public static ModelClient.Usage aggregateUsage(List<EventSink.LoopEvent> events) {
        long promptTokens = 0;
        long completionTokens = 0;
        long totalTokens = 0;
        long cacheHit = 0;
        long cacheMiss = 0;

        for (var event : events) {
            if (event instanceof EventSink.LoopEvent.TurnEnd turnEnd && turnEnd.usage() != null) {
                ModelClient.Usage u = turnEnd.usage();
                promptTokens += u.promptTokens();
                completionTokens += u.completionTokens();
                totalTokens += u.totalTokens();
                cacheHit += u.promptCacheHitTokens();
                cacheMiss += u.promptCacheMissTokens();
            }
        }

        return new ModelClient.Usage(promptTokens, completionTokens, totalTokens, cacheHit, cacheMiss);
    }

    public static int countToolCalls(List<EventSink.LoopEvent> events) {
        return (int) events.stream()
                .filter(e -> e instanceof EventSink.LoopEvent.ToolCallStarted)
                .count();
    }

    public static int countTurns(List<EventSink.LoopEvent> events) {
        return (int) events.stream()
                .filter(e -> e instanceof EventSink.LoopEvent.TurnEnd)
                .count();
    }
}
