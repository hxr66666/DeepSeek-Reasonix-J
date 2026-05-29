package com.reasonix.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrepIndex {

    private static final Logger log = LoggerFactory.getLogger(GrepIndex.class);

    private static final Set<String> IGNORED_DIRS = Set.of(
            "node_modules", ".git", "target", "build", "dist", ".idea", ".vscode",
            "__pycache__", ".gradle", "vendor", "Pods"
    );

    public List<MatchResult> search(Path root, String pattern, String fileGlob, int maxResults) {
        List<MatchResult> results = new ArrayList<>();
        Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);

        try {
            PathMatcher matcher = fileGlob != null ? FileSystems.getDefault().getPathMatcher("glob:" + fileGlob) : null;

            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (IGNORED_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (matcher != null && !matcher.matches(file.getFileName())) {
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        List<String> lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size(); i++) {
                            if (results.size() >= maxResults) return FileVisitResult.TERMINATE;

                            Matcher m = regex.matcher(lines.get(i));
                            if (m.find()) {
                                results.add(new MatchResult(
                                        root.relativize(file).toString(),
                                        i + 1,
                                        lines.get(i).trim(),
                                        m.group()
                                ));
                            }
                        }
                    } catch (IOException ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Grep search failed: {}", e.getMessage());
        }

        return results;
    }

    public record MatchResult(String filePath, int lineNumber, String line, String match) {}
}
