package com.reasonix.code.java;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClassSourceFinder {

    private static final Logger log = LoggerFactory.getLogger(ClassSourceFinder.class);
    private static final int MAX_WALK_DEPTH = 64;
    private static final int DEFAULT_MAX_JAR_SCAN = 2000;
    private static final int JAVAP_TIMEOUT_SECONDS = 30;

    private final Path projectRoot;
    private final List<Path> repoPaths;
    private final String javapCommand;
    private final int maxJarScan;

    public sealed interface FindResult {
        boolean found();
    }

    public record FindResultSuccess(String source, String method, String sourcePath) implements FindResult {
        @Override
        public boolean found() { return true; }
    }

    public record FindResultNotFound() implements FindResult {
        @Override
        public boolean found() { return false; }
    }

    public ClassSourceFinder(Path projectRoot, List<Path> repoPaths) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.repoPaths = repoPaths != null && !repoPaths.isEmpty()
                ? repoPaths.stream().map(Path::toAbsolutePath).map(Path::normalize).toList()
                : defaultRepoPaths();
        this.javapCommand = "javap";
        this.maxJarScan = DEFAULT_MAX_JAR_SCAN;
    }

    public static List<Path> defaultRepoPaths() {
        List<Path> paths = new ArrayList<>();
        String home = System.getProperty("user.home");
        if (home != null) {
            Path m2 = Path.of(home, ".m2", "repository");
            if (Files.exists(m2)) paths.add(m2);
            Path gradle = Path.of(home, ".gradle", "caches");
            if (Files.exists(gradle)) paths.add(gradle);
        }
        return paths;
    }

    public FindResult findSource(String fullyQualifiedName) {
        FindResult projectResult = searchProject(fullyQualifiedName);
        if (projectResult.found()) return projectResult;
        return searchRepositories(fullyQualifiedName, null);
    }

    public FindResult findSourceInJar(String fullyQualifiedName, Path jarPath) {
        if (!Files.exists(jarPath)) {
            return new FindResultNotFound();
        }

        String classEntry = JarReader.toClassEntry(fullyQualifiedName);
        JarReader.JarEntryResult content = JarReader.readEntry(jarPath, classEntry);
        if (content == null) {
            return new FindResultNotFound();
        }

        String source = decompile(jarPath, content.data(), fullyQualifiedName);
        return new FindResultSuccess(source, "jar", jarPath.toString());
    }

    private FindResult searchProject(String fqn) {
        String simpleName = simpleClassName(fqn);
        List<String> suffixes = List.of(simpleName + ".java", simpleName + ".java.txt");

        List<Path> queue = new ArrayList<>();
        queue.add(projectRoot);

        while (!queue.isEmpty()) {
            Path dir = queue.remove(queue.size() - 1);

            try (var stream = Files.list(dir)) {
                for (Path entry : stream.toList()) {
                    if (Files.isDirectory(entry)) {
                        if (shouldSkipDir(entry.getFileName().toString())) continue;
                        queue.add(entry);
                    } else if (Files.isRegularFile(entry)) {
                        String name = entry.getFileName().toString();
                        if (suffixes.contains(name)) {
                            try {
                                String source = Files.readString(entry);
                                return new FindResultSuccess(source, "project", entry.toString());
                            } catch (IOException e) {
                                log.debug("Failed to read {}: {}", entry, e.getMessage());
                            }
                        }
                    }
                }
            } catch (IOException e) {
                log.debug("Failed to list directory {}: {}", dir, e.getMessage());
            }
        }
        return new FindResultNotFound();
    }

    private FindResult searchRepositories(String fqn, String jarKeyword) {
        String javaEntry = JarReader.toJavaEntry(fqn);
        String classEntry = JarReader.toClassEntry(fqn);

        List<Path> sourceJars = new ArrayList<>();
        List<Path> regularJars = new ArrayList<>();

        for (Path repoDir : repoPaths) {
            walkForJars(repoDir, sourceJars, regularJars, jarKeyword, 0);
        }

        for (Path jarPath : sourceJars) {
            String content = JarReader.readEntryAsString(jarPath, javaEntry);
            if (content != null) {
                return new FindResultSuccess(content, "m2-source-jar", jarPath.toString());
            }
        }

        int scanned = 0;
        for (Path jarPath : regularJars) {
            if (scanned >= maxJarScan) break;
            scanned++;

            JarReader.JarEntryResult content = JarReader.readEntry(jarPath, classEntry);
            if (content != null) {
                String source = decompile(jarPath, content.data(), fqn);
                return new FindResultSuccess(source, "m2-jar", jarPath.toString());
            }
        }

        return new FindResultNotFound();
    }

    private void walkForJars(Path dir, List<Path> sourceJars, List<Path> regularJars, String keyword, int depth) {
        if (depth >= MAX_WALK_DEPTH) return;

        try (var stream = Files.list(dir)) {
            for (Path entry : stream.toList()) {
                String name = entry.getFileName().toString();

                if (Files.isDirectory(entry)) {
                    walkForJars(entry, sourceJars, regularJars, keyword, depth + 1);
                } else if (Files.isRegularFile(entry)) {
                    if (name.endsWith("-sources.jar")) {
                        if (keyword == null || name.toLowerCase().contains(keyword.toLowerCase())) {
                            sourceJars.add(entry);
                        }
                    } else if (name.endsWith(".jar")) {
                        if (keyword == null || name.toLowerCase().contains(keyword.toLowerCase())) {
                            regularJars.add(entry);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Failed to walk directory {}: {}", dir, e.getMessage());
        }
    }

    private String decompile(Path jarPath, byte[] classData, String fqn) {
        try {
            ProcessBuilder pb = new ProcessBuilder()
                    .command(javapCommand, "-c", "-p", "-s", "-l", "-verbose",
                            "-classpath", jarPath.toString(), fqn);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean completed = process.waitFor(JAVAP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return "// javap timed out";
            }

            return new String(process.getInputStream().readAllBytes());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "// decompilation failed: " + e.getMessage();
        }
    }

    private String simpleClassName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot > 0 ? fqn.substring(lastDot + 1) : fqn;
    }

    private boolean shouldSkipDir(String name) {
        return switch (name) {
            case "node_modules", ".git", "target", "build", "dist", ".idea", ".vscode", ".gradle", ".m2" -> true;
            default -> name.startsWith(".") || name.startsWith("@");
        };
    }
}