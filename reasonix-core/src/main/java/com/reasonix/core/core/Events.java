package com.reasonix.core.core;

import com.reasonix.core.ports.EventSink;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Events {

    private final List<EventSink.LoopEvent> events = new CopyOnWriteArrayList<>();

    public void add(EventSink.LoopEvent event) {
        events.add(event);
    }

    public List<EventSink.LoopEvent> getAll() {
        return new ArrayList<>(events);
    }

    public List<EventSink.LoopEvent> getRecent(int count) {
        if (events.size() <= count) return getAll();
        return new ArrayList<>(events.subList(events.size() - count, events.size()));
    }

    public void clear() {
        events.clear();
    }

    public int size() {
        return events.size();
    }
}
