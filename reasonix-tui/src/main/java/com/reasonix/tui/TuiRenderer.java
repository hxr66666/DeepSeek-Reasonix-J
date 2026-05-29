package com.reasonix.tui;

public class TuiRenderer {

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GREEN = "\u001B[32m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String GRAY = "\u001B[90m";

    public void renderWelcome() {
        System.out.println();
        System.out.println(CYAN + BOLD + "  ╔══════════════════════════════════════╗" + RESET);
        System.out.println(CYAN + BOLD + "  ║     DeepSeek Reasonix v1.0.0        ║" + RESET);
        System.out.println(CYAN + BOLD + "  ║     AI Programming Assistant         ║" + RESET);
        System.out.println(CYAN + BOLD + "  ╚══════════════════════════════════════╝" + RESET);
        System.out.println();
        System.out.println(GRAY + "  Type /help for commands, /exit to quit" + RESET);
        System.out.println();
    }

    public void renderPrompt() {
        System.out.print(GREEN + "❯ " + RESET);
    }

    public void renderUserMessage(String message) {
        System.out.println(BLUE + "You: " + RESET + message);
    }

    public void renderAssistantMessage(String message) {
        System.out.println(CYAN + "Assistant: " + RESET + message);
    }

    public void renderReasoning(String reasoning) {
        System.out.println(YELLOW + "💭 " + RESET + GRAY + reasoning + RESET);
    }

    public void renderToolCall(String toolName, String toolCallId) {
        System.out.println(YELLOW + "🔧 Calling tool: " + BOLD + toolName + RESET + GRAY + " (" + toolCallId + ")" + RESET);
    }

    public void renderToolResult(String toolName, String result, boolean isError) {
        String color = isError ? "\u001B[31m" : GREEN;
        String prefix = isError ? "✗" : "✓";
        System.out.println(color + prefix + " " + toolName + ": " + RESET + truncate(result, 200));
    }

    public void renderMessage(String message) {
        System.out.println(message);
    }

    public void renderHelp() {
        System.out.println();
        System.out.println(BOLD + "Commands:" + RESET);
        System.out.println("  /help          - Show this help");
        System.out.println("  /exit, /quit   - Exit the application");
        System.out.println("  /model <name>  - Switch model");
        System.out.println("  /clear         - Clear conversation");
        System.out.println("  /memory list   - List memories");
        System.out.println("  /memory add    - Add a memory");
        System.out.println("  /tools         - List available tools");
        System.out.println();
    }

    public void renderClear() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
