package com.reasonix.core.memory;

import com.reasonix.core.ports.ModelClient;

import java.util.ArrayList;
import java.util.List;

public class AppendOnlyLog {

    private List<ModelClient.ChatMessage> entries = new ArrayList<>();

    public void append(ModelClient.ChatMessage message) {
        entries.add(message);
    }

    public void extend(List<ModelClient.ChatMessage> messages) {
        messages.forEach(this::append);
    }

    public void compactInPlace(List<ModelClient.ChatMessage> replacement) {
        this.entries = new ArrayList<>(replacement);
    }

    public List<ModelClient.ChatMessage> toMessages() {
        return new ArrayList<>(entries);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public ModelClient.ChatMessage last() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    public List<ModelClient.ChatMessage> tail(int count) {
        if (count >= entries.size()) return new ArrayList<>(entries);
        return new ArrayList<>(entries.subList(entries.size() - count, entries.size()));
    }

    public List<ModelClient.ChatMessage> head(int count) {
        if (count >= entries.size()) return new ArrayList<>(entries);
        return new ArrayList<>(entries.subList(0, count));
    }

    public void clear() {
        this.entries = new ArrayList<>();
    }
}
