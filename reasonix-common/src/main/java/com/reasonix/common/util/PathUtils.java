package com.reasonix.common.util;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class PathUtils {

    private PathUtils() {}

    public static Path getReasonixHome() {
        return Paths.get(System.getProperty("user.home"), ".reasonix");
    }

    public static Path getGlobalConfigPath() {
        return getReasonixHome().resolve("config.yaml");
    }

    public static Path getSessionsDir() {
        return getReasonixHome().resolve("sessions");
    }

    public static Path getSkillsDir() {
        return getReasonixHome().resolve("skills");
    }

    public static Path getProjectConfigDir(Path projectRoot) {
        return projectRoot.resolve(".reasonix");
    }

    public static Path getProjectSkillsDir(Path projectRoot) {
        return getProjectConfigDir(projectRoot).resolve("skills");
    }

    public static boolean isUnder(Path path, Path root) {
        return path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize());
    }

    public static Path resolveSafe(Path root, String relativePath) {
        Path resolved = root.resolve(relativePath).toAbsolutePath().normalize();
        if (!isUnder(resolved, root.toAbsolutePath().normalize())) {
            throw new SecurityException("Path escape detected: " + relativePath);
        }
        return resolved;
    }

    public static String sanitizeForFilename(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
