package com.reasonix.core.tools.shell;

import com.reasonix.core.ports.ToolHost;
import com.reasonix.core.tools.ToolContext;
import com.reasonix.core.tools.ToolDefinition;
import com.reasonix.core.tools.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ShellTool implements ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ShellTool.class);

    private static final long DEFAULT_TIMEOUT_MS = 120_000;
    private static final List<String> BLOCKED_PATTERNS = List.of(
            // 递归删除
            "rm\\s+-rf", "rm\\s+--recursive", "rm\\s+--remove-destination",
            // 格式化
            "mkfs", "format", "fdisk",
            // 磁盘写入
            "dd\\s+if=", "dd\\s+of=",
            // 进程炸弹
            ":\\s*\\(\\s*\\)\\s*\\{\\s*:\\s*\\|\\s*:\\s*;\\s*\\}",
            // 网络下载可能导致恶意代码
            "wget", "curl",
            // 权限更改可能导致安全漏洞
            "chmod\\s+777",
            // 系统关键文件
            "/etc/passwd", "/etc/shadow", "/etc/sudoers",
            // 覆盖根分区
            ">\\s*/dev/sd", "mv\\s+[^ ]*\\s*/"
    );

    @Override
    public ToolHost.ToolResult execute(String toolName, Map<String, Object> arguments, ToolContext context) {
        String command = (String) arguments.get("command");
        if (command == null || command.isBlank()) {
            return ToolHost.ToolResult.failure("No command provided");
        }

        for (String pattern : BLOCKED_PATTERNS) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
                    .matcher(command).find()) {
                return ToolHost.ToolResult.failure("Blocked dangerous command pattern: " + pattern);
            }
        }

        long timeout = arguments.containsKey("timeout")
                ? ((Number) arguments.get("timeout")).longValue()
                : DEFAULT_TIMEOUT_MS;

        Path cwd = context != null ? context.getWorkspaceRoot() : Path.of(".");

        try {
            ProcessBuilder pb = new ProcessBuilder()
                    .command("sh", "-c", command)
                    .directory(cwd.toFile())
                    .redirectErrorStream(true);

            Process process = pb.start();

            boolean completed = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                return ToolHost.ToolResult.failure("Command timed out after " + timeout + "ms");
            }

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.exitValue();

            if (exitCode == 0) {
                return ToolHost.ToolResult.success(output);
            } else {
                return ToolHost.ToolResult.failure("Exit code " + exitCode + "\n" + output);
            }

        } catch (IOException | InterruptedException e) {
            return ToolHost.ToolResult.failure("Execution failed: " + e.getMessage());
        }
    }

    public static ToolDefinition toToolDefinition() {
        return ToolDefinition.builder()
                .name("shell")
                .description("Execute a shell command in the workspace directory")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "command", Map.of("type", "string", "description", "The shell command to execute"),
                                "timeout", Map.of("type", "integer", "description", "Timeout in milliseconds")
                        ),
                        "required", List.of("command")
                ))
                .parallelSafe(false)
                .executor(new ShellTool())
                .build();
    }
}
