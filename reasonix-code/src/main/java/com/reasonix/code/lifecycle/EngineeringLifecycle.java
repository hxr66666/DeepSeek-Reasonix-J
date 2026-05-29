package com.reasonix.code.lifecycle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

public class EngineeringLifecycle {

    public enum Mode { off, strict }

    public enum State { idle, armed, planning, approved, executing, checkpoint, complete, cancelled }

    public enum RiskLevel { safe, low, med, high }

    public record PlanStep(String id, String description, RiskLevel risk, List<String> verification) {}

    public record Snapshot(Mode mode, State state, List<PlanStep> planSteps, Set<String> completedStepIds, boolean mutatedSinceLastStep) {}

    public record ToolInterceptorResult(boolean blocked, String error, String rejectedReason, State nextState, String nextAction) {}

    private Mode _mode = Mode.off;
    private State _state = State.idle;
    private final List<PlanStep> _planSteps = new ArrayList<>();
    private final Set<String> _completedStepIds = new HashSet<>();
    private boolean _mutatedSinceLastStep = false;

    private BiFunction<String, java.util.Map<String, Object>, ToolInterceptorResult> _toolInterceptor;

    public EngineeringLifecycle() {
        this._toolInterceptor = (name, args) -> defaultInterceptor(name, args, this);
    }

    public EngineeringLifecycle(Mode mode) {
        this();
        this._mode = mode;
        if (mode == Mode.strict) this._state = State.armed;
    }

    public Mode mode() { return _mode; }

    public void setMode(Mode mode) {
        this._mode = mode;
        if (mode == Mode.off) {
            reset();
            return;
        }
        if (mode == Mode.strict && _state == State.idle) _state = State.armed;
    }

    public State state() { return _state; }

    public void observeUserPrompt() {
        if (_mode == Mode.off) return;
        if (_state == State.complete || _state == State.cancelled) reset();
        if (_state == State.idle) _state = State.armed;
    }

    public void recordPlanProposed(List<PlanStep> steps) {
        if (_mode == Mode.off) return;
        _state = State.planning;
        _planSteps.clear();
        if (steps != null) _planSteps.addAll(steps);
        _completedStepIds.clear();
        _mutatedSinceLastStep = false;
    }

    public void recordPlanApproved(List<PlanStep> steps) {
        if (_mode == Mode.off) return;
        _state = State.approved;
        if (steps != null) {
            _planSteps.clear();
            _planSteps.addAll(steps);
        }
        _completedStepIds.clear();
        _mutatedSinceLastStep = false;
    }

    public void recordPlanRevised(List<PlanStep> remainingSteps) {
        if (_mode == Mode.off) return;

        List<PlanStep> donePrefix = new ArrayList<>();
        for (PlanStep step : _planSteps) {
            if (_completedStepIds.contains(step.id())) donePrefix.add(step);
        }

        List<PlanStep> merged = new ArrayList<>(donePrefix);
        if (remainingSteps != null) {
            for (PlanStep step : remainingSteps) {
                if (!_completedStepIds.contains(step.id())) merged.add(step);
            }
        }

        _planSteps.clear();
        _planSteps.addAll(merged);

        if (!_planSteps.isEmpty() && _completedStepIds.size() >= _planSteps.size()) {
            _state = State.complete;
        } else {
            _state = State.executing;
        }
    }

    public void recordCheckpointReached() {
        if (_mode == Mode.off) return;
        if (_state == State.approved || _state == State.executing) {
            _state = State.checkpoint;
        }
    }

    public void recordStepCompleted(String stepId) {
        if (stepId == null || stepId.isBlank()) return;
        _completedStepIds.add(stepId);
        _mutatedSinceLastStep = false;

        if (!_planSteps.isEmpty() && _completedStepIds.size() >= _planSteps.size()) {
            _state = State.complete;
        } else if (_state != State.idle && _state != State.cancelled) {
            _state = State.executing;
        }
    }

    public void recordToolResult(String name, java.util.Map<String, Object> args, String result) {
        if (_mode == Mode.off) return;
        if (!isLifecycleMutationToolCall(name, args)) return;
        if (!toolResultLooksSuccessful(result)) return;

        if (_state == State.approved || _state == State.executing) {
            _state = State.executing;
            _mutatedSinceLastStep = true;
        }
    }

    public void cancel() {
        _state = State.cancelled;
        _planSteps.clear();
        _completedStepIds.clear();
        _mutatedSinceLastStep = false;
    }

    public void reset() {
        _state = _mode == Mode.strict ? State.armed : State.idle;
        _planSteps.clear();
        _completedStepIds.clear();
        _mutatedSinceLastStep = false;
    }

    public ToolInterceptorResult interceptToolCall(String name, java.util.Map<String, Object> args) {
        return _toolInterceptor.apply(name, args);
    }

    public void setToolInterceptor(BiFunction<String, java.util.Map<String, Object>, ToolInterceptorResult> interceptor) {
        this._toolInterceptor = interceptor;
    }

    public Snapshot snapshot() {
        return new Snapshot(_mode, _state, new ArrayList<>(_planSteps), new HashSet<>(_completedStepIds), _mutatedSinceLastStep);
    }

    public static boolean isHighRiskToolCall(String name, java.util.Map<String, Object> args) {
        return classifyToolCall(name, args) == RiskLevel.high;
    }

    public static boolean isLifecycleMutationToolCall(String name, java.util.Map<String, Object> args) {
        return classifyToolCall(name, args) != RiskLevel.safe;
    }

    public static RiskLevel classifyToolCall(String name, java.util.Map<String, Object> args) {
        if (name == null) return RiskLevel.safe;

        return switch (name) {
            case "write_file", "create_file" -> RiskLevel.high;
            case "delete_file", "move_file", "rename_file" -> RiskLevel.high;
            case "shell", "bash", "exec" -> RiskLevel.high;
            case "edit_file", "apply_diff" -> RiskLevel.med;
            case "read_file", "search_content", "list_directory", "grep" -> RiskLevel.safe;
            case "mark_step_complete", "checkpoint", "confirm" -> RiskLevel.low;
            default -> RiskLevel.med;
        };
    }

    private static final Set<String> HIGH_RISK_TOOLS = Set.of(
            "write_file", "create_file", "delete_file", "move_file", "rename_file",
            "shell", "bash", "exec", "apply_diff"
    );

    private static final Set<String> MUTATION_TOOLS = Set.of(
            "write_file", "create_file", "delete_file", "move_file", "rename_file",
            "shell", "bash", "exec", "apply_diff", "edit_file"
    );

    private ToolInterceptorResult defaultInterceptor(String name, java.util.Map<String, Object> args, EngineeringLifecycle lifecycle) {
        if (lifecycle._mode == Mode.off) {
            return new ToolInterceptorResult(false, null, null, lifecycle._state, null);
        }

        if ("mark_step_complete".equals(name)) {
            return guardStepCompletion(args, lifecycle);
        }

        if (!isHighRiskToolCall(name, args)) {
            return new ToolInterceptorResult(false, null, null, lifecycle._state, null);
        }

        if (lifecycle._state != State.approved && lifecycle._state != State.executing) {
            String error = name + ": blocked by Engineering Lifecycle — submit an approved plan before high-risk mutation.";
            return new ToolInterceptorResult(true, error, "engineering-lifecycle", lifecycle._state, "submit_plan");
        }

        lifecycle._state = State.executing;
        return new ToolInterceptorResult(false, null, null, lifecycle._state, null);
    }

    private ToolInterceptorResult guardStepCompletion(java.util.Map<String, Object> args, EngineeringLifecycle lifecycle) {
        String stepId = args.get("stepId") instanceof String s ? s.trim() : "";
        PlanStep step = lifecycle._planSteps.stream().filter(s -> s.id().equals(stepId)).findFirst().orElse(null);

        @SuppressWarnings("unchecked")
        List<String> evidence = args.get("evidence") instanceof List l ? (List<String>) (List<?>) l : List.of();

        boolean evidenceRequired = lifecycle._mutatedSinceLastStep
                || (step != null && (step.risk() == RiskLevel.med || step.risk() == RiskLevel.high))
                || (step != null && step.verification() != null && !step.verification().isEmpty());

        if (evidenceRequired && evidence.isEmpty()) {
            String error = "mark_step_complete: evidence required — add verification, diff, checkpoint, or manual evidence.";
            return new ToolInterceptorResult(true, error, "engineering-lifecycle-evidence", lifecycle._state, "add_evidence");
        }

        return new ToolInterceptorResult(false, null, null, lifecycle._state, null);
    }

    private boolean toolResultLooksSuccessful(String result) {
        if (result == null || result.isBlank()) return false;

        String text = result.trim();
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(text, java.util.Map.class);
            if (parsed != null && parsed.containsKey("error")) return false;
        } catch (Exception ignored) {}

        if (text.matches("(?i).*\\b0/\\d+\\s+applied\\b.*")) return false;

        return !text.matches("(?i).*(user rejected|rejected this edit|discarded|unavailable in plan mode|interceptor failed|\\berror\\b|failed).*");
    }
}