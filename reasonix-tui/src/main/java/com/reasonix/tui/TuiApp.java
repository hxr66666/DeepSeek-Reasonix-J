package com.reasonix.tui;

import com.reasonix.core.ports.EventSink;
import com.reasonix.core.ports.ModelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

public class TuiApp {

    private static final Logger log = LoggerFactory.getLogger(TuiApp.class);

    private final TuiRenderer renderer;
    private final TuiInputHandler inputHandler;
    private volatile boolean running = true;

    public TuiApp() {
        this.renderer = new TuiRenderer();
        this.inputHandler = new TuiInputHandler();
    }

    public void start() {
        renderer.renderWelcome();

        try (Scanner scanner = new Scanner(System.in)) {
            while (running) {
                renderer.renderPrompt();
                String input = scanner.nextLine();

                if (input == null || input.isBlank()) continue;

                if (input.startsWith("/")) {
                    handleCommand(input.trim());
                    continue;
                }

                renderer.renderUserMessage(input);
            }
        }
    }

    private void handleCommand(String command) {
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "/exit", "/quit" -> {
                renderer.renderMessage("Goodbye!");
                running = false;
            }
            case "/help" -> renderer.renderHelp();
            case "/model" -> renderer.renderMessage("Current model: " + args);
            case "/clear" -> renderer.renderClear();
            case "/memory" -> renderer.renderMessage("Memory commands: /memory list, /memory add <text>");
            case "/tools" -> renderer.renderMessage("Available tools: shell, read_file, write_file, list_directory, search_content");
            default -> renderer.renderMessage("Unknown command: " + cmd);
        }
    }

    public void stop() {
        running = false;
    }
}
