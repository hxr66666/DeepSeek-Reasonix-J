package com.reasonix.code.edit;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EditBlocks {

    public record EditBlock(String path, String search, String replace, int offset) {}

    public enum ApplyStatus {
        applied,
        created,
        not_found,
        file_missing,
        path_escape,
        error
    }

    public record ApplyResult(String path, ApplyStatus status, String message) {}

    public static final Pattern BLOCK_RE = Pattern.compile(
            "^(\\S[^\\n]*)\\n<{7} SEARCH\\n([\\s\\S]*?)\\n?={7}\\n([\\s\\S]*?)\\n?>{7} REPLACE",
            Pattern.MULTILINE
    );

    public static List<EditBlock> parse(String text) {
        List<EditBlock> blocks = new ArrayList<>();
        Matcher m = BLOCK_RE.matcher(text);
        while (m.find()) {
            blocks.add(new EditBlock(
                    m.group(1).trim(),
                    m.group(2),
                    m.group(3),
                    m.start()
            ));
        }
        return blocks;
    }

    public static ApplyResult apply(EditBlock block, String rootDir) {
        Path absRoot = Paths.get(rootDir).toAbsolutePath().normalize();
        Path absTarget = resolvePath(rootDir, block.path());

        if (!pathIsUnder(absTarget, absRoot)) {
            return new ApplyResult(block.path(), ApplyStatus.path_escape,
                    "resolved path is outside rootDir");
        }

        boolean searchEmpty = block.search().isEmpty();

        if (searchEmpty) {
            return createNewFile(block, absTarget);
        }

        return replaceInFile(block, absTarget);
    }

    private static ApplyResult createNewFile(EditBlock block, Path absTarget) {
        try {
            Files.createDirectories(absTarget.getParent());
            Files.writeString(absTarget, block.replace());
            return new ApplyResult(block.path(), ApplyStatus.created, null);
        } catch (IOException e) {
            if (e instanceof java.nio.file.FileAlreadyExistsException) {
                return new ApplyResult(block.path(), ApplyStatus.not_found,
                        "empty SEARCH only creates new files — this file already exists");
            }
            return new ApplyResult(block.path(), ApplyStatus.error, e.getMessage());
        }
    }

    private static ApplyResult replaceInFile(EditBlock block, Path absTarget) {
        if (!Files.exists(absTarget)) {
            return new ApplyResult(block.path(), ApplyStatus.file_missing,
                    "file does not exist; to create it, use an empty SEARCH block");
        }

        try {
            String content = Files.readString(absTarget);
            String le = lineEndingOf(content);
            String adaptedSearch = block.search().replace("\r\n", le).replace("\n", le);
            String adaptedReplace = block.replace().replace("\r\n", le).replace("\n", le);

            int idx = content.indexOf(adaptedSearch);
            if (idx == -1) {
                return new ApplyResult(block.path(), ApplyStatus.not_found,
                        "SEARCH text does not match the current file content exactly");
            }

            int nextIdx = content.indexOf(adaptedSearch, idx + 1);
            if (nextIdx != -1) {
                return new ApplyResult(block.path(), ApplyStatus.not_found,
                        "SEARCH text appears multiple times; include more context to disambiguate");
            }

            String replaced = content.substring(0, idx) + adaptedReplace + content.substring(idx + adaptedSearch.length());
            Files.writeString(absTarget, replaced);
            return new ApplyResult(block.path(), ApplyStatus.applied, null);

        } catch (IOException e) {
            return new ApplyResult(block.path(), ApplyStatus.error, e.getMessage());
        }
    }

    private static Path resolvePath(String rootDir, String rawPath) {
        Path absRoot = Paths.get(rootDir).toAbsolutePath().normalize();

        if (Paths.get(rawPath).isAbsolute()) {
            return Paths.get(rawPath).toAbsolutePath().normalize();
        }

        String rooted = rawPath.replaceFirst("^[/\\\\]+", "");
        return absRoot.resolve(rooted.isEmpty() ? "." : rooted).normalize();
    }

    private static boolean pathIsUnder(Path child, Path parent) {
        Path absChild = child.toAbsolutePath().normalize();
        Path absParent = parent.toAbsolutePath().normalize();
        String rel = absParent.relativize(absChild).toString();
        return !rel.startsWith("..");
    }

    private static String lineEndingOf(String text) {
        return text.contains("\r\n") ? "\r\n" : "\n";
    }
}