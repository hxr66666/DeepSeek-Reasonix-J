package com.reasonix.core.hooks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class HookManager {

    private static final Logger log = LoggerFactory.getLogger(HookManager.class);

    private final List<ResolvedHook> hooks;
    private final boolean trustProjectHooks;

    public HookManager(List<ResolvedHook> hooks, boolean trustProjectHooks) {
        this.hooks = hooks;
        this.trustProjectHooks = trustProjectHooks;
    }

    public HookReport runHooks(HookEvent event, HookContext context) {
        List<HookOutcome> outcomes = new ArrayList<>();
        boolean blocked = false;

        for (ResolvedHook hook : hooks) {
            if (hook.event() != event) continue;
            if (hook.match() != null && !"*".equals(hook.match())) {
                if (context.toolName() == null || !context.toolName().matches(hook.match())) {
                    continue;
                }
            }

            HookOutcome outcome = executeHook(hook, context);
            outcomes.add(outcome);

            if (outcome.decision() == HookOutcomeDecision.BLOCK) {
                blocked = true;
                if (isBlockingEvent(event)) break;
            }
        }

        return new HookReport(event, outcomes, blocked);
    }

    private HookOutcome executeHook(ResolvedHook hook, HookContext context) {
        long startTime = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder()
                    .command("sh", "-c", hook.command())
                    .redirectErrorStream(false);

            Map<String, String> env = pb.environment();
            env.put("REASONIX_HOOK_EVENT", hook.event().name());
            if (context.toolName() != null) {
                env.put("REASONIX_TOOL_NAME", context.toolName());
            }
            if (hook.cwd() != null) {
                pb.directory(Path.of(hook.cwd()).toFile());
            }

            Process process = pb.start();
            boolean completed = process.waitFor(hook.timeoutMs(), TimeUnit.MILLISECONDS);

            String stdout = new String(process.getInputStream().readAllBytes()).trim();
            String stderr = new String(process.getErrorStream().readAllBytes()).trim();
            long duration = System.currentTimeMillis() - startTime;

            if (!completed) {
                process.destroyForcibly();
                return new HookOutcome(hook, HookOutcomeDecision.TIMEOUT, null, stdout, stderr, duration);
            }

            int exitCode = process.exitValue();
            HookOutcomeDecision decision = switch (exitCode) {
                case 0 -> HookOutcomeDecision.PASS;
                case 2 -> isBlockingEvent(hook.event()) ? HookOutcomeDecision.BLOCK : HookOutcomeDecision.WARN;
                default -> HookOutcomeDecision.WARN;
            };
            return new HookOutcome(hook, decision, exitCode, stdout, stderr, duration);

        } catch (IOException | InterruptedException e) {
            return new HookOutcome(hook, HookOutcomeDecision.ERROR, null, "", e.getMessage(),
                    System.currentTimeMillis() - startTime);
        }
    }

    private boolean isBlockingEvent(HookEvent event) {
        return event == HookEvent.PreToolUse || event == HookEvent.UserPromptSubmit;
    }

    public record ResolvedHook(
            HookEvent event, String command, String match, String cwd,
            long timeoutMs, HookScope scope
    ) {}

    public enum HookScope { GLOBAL, PROJECT }

    public record HookOutcome(
            ResolvedHook hook, HookOutcomeDecision decision, Integer exitCode,
            String stdout, String stderr, long durationMs
    ) {}

    public enum HookOutcomeDecision { PASS, BLOCK, WARN, TIMEOUT, ERROR }

    public record HookReport(HookEvent event, List<HookOutcome> outcomes, boolean blocked) {}
}
