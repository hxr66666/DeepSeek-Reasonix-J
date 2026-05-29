package com.reasonix.core.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VolatileScratch {

    public String reasoning;
    public Map<String, Object> planState;
    public List<String> notes;

    public VolatileScratch() {
        this.notes = new ArrayList<>();
    }

    public void reset() {
        reasoning = null;
        planState = null;
        notes.clear();
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public void setPlanState(Map<String, Object> planState) {
        this.planState = planState != null ? new HashMap<>(planState) : null;
    }

    public void addNote(String note) {
        notes.add(note);
    }

    public String getReasoning() {
        return reasoning;
    }

    public Map<String, Object> getPlanState() {
        return planState != null ? new HashMap<>(planState) : null;
    }

    public List<String> getNotes() {
        return new ArrayList<>(notes);
    }
}
