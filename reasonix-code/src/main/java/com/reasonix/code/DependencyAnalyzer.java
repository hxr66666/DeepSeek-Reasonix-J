package com.reasonix.code;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class DependencyAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(DependencyAnalyzer.class);

    private final Path workspaceRoot;
    private final Map<String, Set<String>> dependencyGraph = new HashMap<>();

    public DependencyAnalyzer(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public Map<String, Set<String>> analyze() {
        dependencyGraph.clear();

        try {
            Files.walkFileTree(workspaceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (name.equals("pom.xml")) {
                        analyzeMavenDeps(file);
                    } else if (name.equals("build.gradle") || name.equals("build.gradle.kts")) {
                        analyzeGradleDeps(file);
                    } else if (name.equals("package.json")) {
                        analyzeNpmDeps(file);
                    } else if (name.equals("requirements.txt") || name.equals("pyproject.toml")) {
                        analyzePythonDeps(file);
                    } else if (name.equals("Cargo.toml")) {
                        analyzeRustDeps(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Failed to analyze dependencies: {}", e.getMessage());
        }

        return Collections.unmodifiableMap(dependencyGraph);
    }

    private void analyzeMavenDeps(Path pomFile) {
        String moduleName = workspaceRoot.relativize(pomFile.getParent()).toString();
        Set<String> deps = new HashSet<>();

        try {
            String content = Files.readString(pomFile);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "<groupId>([^<]+)</groupId>\\s*<artifactId>([^<]+)</artifactId>"
            ).matcher(content);

            while (matcher.find()) {
                deps.add(matcher.group(1) + ":" + matcher.group(2));
            }
        } catch (IOException e) {
            log.debug("Failed to read pom.xml: {}", pomFile);
        }

        dependencyGraph.put(moduleName, deps);
    }

    private void analyzeGradleDeps(Path buildFile) {
        String moduleName = workspaceRoot.relativize(buildFile.getParent()).toString();
        Set<String> deps = new HashSet<>();

        try {
            String content = Files.readString(buildFile);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "(?:implementation|api|compileOnly|runtimeOnly)\\s+[\"']([^:]+):([^:]+):([^\"']+)[\"']"
            ).matcher(content);

            while (matcher.find()) {
                deps.add(matcher.group(1) + ":" + matcher.group(2));
            }
        } catch (IOException e) {
            log.debug("Failed to read build.gradle: {}", buildFile);
        }

        dependencyGraph.put(moduleName, deps);
    }

    private void analyzeNpmDeps(Path packageJson) {
        String moduleName = workspaceRoot.relativize(packageJson.getParent()).toString();
        Set<String> deps = new HashSet<>();

        try {
            String content = Files.readString(packageJson);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "\"([^\"]+)\":\\s*\"[^\"]+\""
            ).matcher(content);
            while (matcher.find()) {
                deps.add("npm:" + matcher.group(1));
            }
        } catch (IOException e) {
            log.debug("Failed to read package.json: {}", packageJson);
        }

        dependencyGraph.put(moduleName, deps);
    }

    private void analyzePythonDeps(Path depFile) {
        String moduleName = workspaceRoot.relativize(depFile.getParent()).toString();
        Set<String> deps = new HashSet<>();

        try {
            String content = Files.readString(depFile);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "^([a-zA-Z0-9_-]+)", java.util.regex.Pattern.MULTILINE
            ).matcher(content);

            while (matcher.find()) {
                deps.add("pypi:" + matcher.group(1));
            }
        } catch (IOException e) {
            log.debug("Failed to read Python deps: {}", depFile);
        }

        dependencyGraph.put(moduleName, deps);
    }

    private void analyzeRustDeps(Path cargoToml) {
        String moduleName = workspaceRoot.relativize(cargoToml.getParent()).toString();
        Set<String> deps = new HashSet<>();

        try {
            String content = Files.readString(cargoToml);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "([a-zA-Z0-9_-]+)\\s*=\\s*\\{?\\s*version\\s*=\\s*\"([^\"]+)\""
            ).matcher(content);

            while (matcher.find()) {
                deps.add("crate:" + matcher.group(1));
            }
        } catch (IOException e) {
            log.debug("Failed to read Cargo.toml: {}", cargoToml);
        }

        dependencyGraph.put(moduleName, deps);
    }
}
