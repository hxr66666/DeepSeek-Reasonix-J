package com.reasonix.core.workspaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

public class WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);

    private final List<Path> recentWorkspaces;
    private Path currentWorkspace;

    public WorkspaceManager() {
        this.recentWorkspaces = new ArrayList<>();
    }

    public List<WorkspaceInfo> listKnownWorkspaces() {
        Map<String, WorkspaceInfo> seen = new LinkedHashMap<>();

        if (currentWorkspace != null) {
            addWorkspace(seen, currentWorkspace, true);
        }
        for (Path path : recentWorkspaces) {
            addWorkspace(seen, path, false);
        }

        return new ArrayList<>(seen.values());
    }

    public void setCurrentWorkspace(Path path) {
        this.currentWorkspace = path;
        rememberWorkspace(path);
    }

    public Path getCurrentWorkspace() {
        return currentWorkspace;
    }

    public void rememberWorkspace(Path path) {
        Path resolved = path.toAbsolutePath().normalize();
        recentWorkspaces.remove(resolved);
        recentWorkspaces.add(0, resolved);
        if (recentWorkspaces.size() > 20) {
            recentWorkspaces.subList(20, recentWorkspaces.size()).clear();
        }
    }

    private void addWorkspace(Map<String, WorkspaceInfo> seen, Path path, boolean current) {
        if (path == null) return;
        Path resolved = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(resolved)) return;

        String key = resolved.toString().toLowerCase();
        WorkspaceInfo existing = seen.get(key);

        if (existing != null) {
            seen.put(key, new WorkspaceInfo(resolved, existing.current() || current, existing.sessions()));
            return;
        }

        seen.put(key, new WorkspaceInfo(resolved, current, 0));
    }

    public record WorkspaceInfo(Path path, boolean current, int sessions) {}
}
