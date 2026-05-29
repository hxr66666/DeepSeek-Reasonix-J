package com.reasonix.core.memory;

import com.reasonix.common.util.JsonUtils;
import com.reasonix.core.ports.ModelClient;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;
import java.util.List;

public class ImmutablePrefix {

    private String system;
    private final List<ModelClient.ToolSpec> toolSpecs;
    private final List<ModelClient.ChatMessage> fewShots;
    private String fingerprintCache;

    public ImmutablePrefix(String system) {
        this.system = system;
        this.toolSpecs = new ArrayList<>();
        this.fewShots = new ArrayList<>();
    }

    public ImmutablePrefix(String system, List<ModelClient.ToolSpec> toolSpecs, List<ModelClient.ChatMessage> fewShots) {
        this.system = system;
        this.toolSpecs = new ArrayList<>(toolSpecs);
        this.fewShots = new ArrayList<>(fewShots);
    }

    public String getFingerprint() {
        if (fingerprintCache != null) return fingerprintCache;
        String blob = system + JsonUtils.toJson(toolSpecs) + JsonUtils.toJson(fewShots);
        fingerprintCache = DigestUtils.sha256Hex(blob).substring(0, 16);
        return fingerprintCache;
    }

    public void replaceSystem(String s) {
        if (this.system.equals(s)) return;
        this.system = s;
        this.fingerprintCache = null;
    }

    public boolean addTool(ModelClient.ToolSpec spec) {
        if (toolSpecs.stream().anyMatch(t -> t.name().equals(spec.name()))) {
            return false;
        }
        toolSpecs.add(spec);
        this.fingerprintCache = null;
        return true;
    }

    public boolean removeTool(String toolName) {
        boolean removed = toolSpecs.removeIf(t -> t.name().equals(toolName));
        if (removed) this.fingerprintCache = null;
        return removed;
    }

    public void addFewShot(ModelClient.ChatMessage message) {
        fewShots.add(message);
        this.fingerprintCache = null;
    }

    public String getSystem() {
        return system;
    }

    public List<ModelClient.ToolSpec> getToolSpecs() {
        return List.copyOf(toolSpecs);
    }

    public List<ModelClient.ChatMessage> getFewShots() {
        return List.copyOf(fewShots);
    }
}
