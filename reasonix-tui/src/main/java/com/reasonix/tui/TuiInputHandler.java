package com.reasonix.tui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

public class TuiInputHandler {

    private static final Logger log = LoggerFactory.getLogger(TuiInputHandler.class);

    public String readMultilineInput(Scanner scanner) {
        StringBuilder sb = new StringBuilder();
        String line;

        while (scanner.hasNextLine()) {
            line = scanner.nextLine();
            if (line.equals(".")) break;
            if (line.equals("\\q")) return null;
            sb.append(line).append("\n");
        }

        return sb.toString().trim();
    }

    public boolean isCommand(String input) {
        return input != null && input.startsWith("/");
    }

    public String parseCommand(String input) {
        if (input == null || !input.startsWith("/")) return null;
        String[] parts = input.split("\\s+", 2);
        return parts[0].toLowerCase();
    }

    public String parseCommandArgs(String input) {
        if (input == null || !input.startsWith("/")) return null;
        String[] parts = input.split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "";
    }
}
