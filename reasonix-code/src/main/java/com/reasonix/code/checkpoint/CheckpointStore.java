package com.reasonix.code.checkpoint;

import com.reasonix.common.util.JsonUtils;
import com.reasonix.common.util.PathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CheckpointStore {

    public record CheckpointFile(String path, String content) {}
    public record Checkpoint(String id, String name, String rootDir, long createdAt, String source, List<CheckpointFile> files, int bytes) {}
    public record CheckpointMeta(String id, String name, long createdAt, String source, int fileCount, int bytes) {}

    public record CreateOptions(String rootDir, String name, String source, List<String> paths) {}

    public record RestoreResult(List<String> restored, List<String> removed, List<Skipped> skipped) {
        public record Skipped(String path, String reason) {}
    }

    public static List<CheckpointMeta> list(String rootDir) {
        Path indexPath = indexPath(rootDir);
        if (!Files.exists(indexPath)) return List.of();
        try {
            String json = Files.readString(indexPath);
            return JsonUtils.mapper().readValue(json,
                    JsonUtils.mapper().getTypeFactory().constructCollectionType(List.class, CheckpointMeta.class));
        } catch (IOException e) {
            return List.of();
        }
    }

    public static Checkpoint load(String rootDir, String id) {
        Path path = snapshotPath(rootDir, id);
        if (!Files.exists(path)) return null;
        try {
            return JsonUtils.parse(Files.readString(path), Checkpoint.class);
        } catch (IOException e) {
            return null;
        }
    }

    public static CheckpointMeta create(CreateOptions opts) {
        String absRoot = Paths.get(opts.rootDir()).toAbsolutePath().normalize().toString();
        String id = "cp-" + Long.toString(System.currentTimeMillis(), 36) + "-" + Long.toString(Double.doubleToLongBits(Math.random()), 36).substring(0, 4);
        List<CheckpointFile> files = new ArrayList<>();
        int bytes = 0;

        for (String p : opts.paths()) {
            Path abs = Paths.get(absRoot, p).normalize();
            if (!PathUtils.isUnder(abs, Paths.get(absRoot))) continue;

            Path absPath = Paths.get(absRoot).relativize(abs);
            String rel = absPath.toString().replace('\\', '/');

            if (Files.exists(abs)) {
                try {
                    String content = Files.readString(abs);
                    files.add(new CheckpointFile(rel, content));
                    bytes += content.length();
                } catch (IOException e) {
                    files.add(new CheckpointFile(rel, null));
                }
            } else {
                files.add(new CheckpointFile(rel, null));
            }
        }

        Checkpoint checkpoint = new Checkpoint(
                id, opts.name(), absRoot, System.currentTimeMillis(),
                opts.source() != null ? opts.source() : "manual", files, bytes
        );

        try {
            Path cpPath = snapshotPath(absRoot, id);
            Files.createDirectories(cpPath.getParent());
            Files.writeString(cpPath, JsonUtils.toJson(checkpoint));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write checkpoint", e);
        }

        List<CheckpointMeta> items = new ArrayList<>(list(absRoot));
        CheckpointMeta meta = new CheckpointMeta(id, opts.name(), checkpoint.createdAt(), checkpoint.source(), files.size(), bytes);
        items.add(meta);
        writeIndex(absRoot, items);

        return meta;
    }

    public static CheckpointMeta find(String rootDir, String idOrName) {
        List<CheckpointMeta> items = list(rootDir);
        return items.stream()
                .filter(m -> m.id().equals(idOrName))
                .findFirst()
                .orElseGet(() -> items.stream()
                        .reduce((first, second) -> second)
                        .filter(m -> m.name().equals(idOrName))
                        .orElse(null));
    }

    public static RestoreResult restore(String rootDir, String id) {
        Checkpoint cp = load(rootDir, id);
        String absRoot = Paths.get(rootDir).toAbsolutePath().normalize().toString();
        RestoreResult result = new RestoreResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        if (cp == null) {
            result.skipped().add(new RestoreResult.Skipped("(checkpoint)", "not found: " + id));
            return result;
        }

        for (CheckpointFile f : cp.files()) {
            Path abs = Paths.get(absRoot, f.path()).normalize();
            if (!PathUtils.isUnder(abs, Paths.get(absRoot))) {
                result.skipped().add(new RestoreResult.Skipped(f.path(), "path escapes rootDir"));
                continue;
            }

            try {
                if (f.content() == null) {
                    if (Files.exists(abs)) {
                        Files.delete(abs);
                        result.removed().add(f.path());
                    }
                } else {
                    Files.createDirectories(abs.getParent());
                    Files.writeString(abs, f.content());
                    result.restored().add(f.path());
                }
            } catch (IOException e) {
                result.skipped().add(new RestoreResult.Skipped(f.path(), e.getMessage()));
            }
        }

        return result;
    }

    public static boolean delete(String rootDir, String id) {
        Path cpPath = snapshotPath(rootDir, id);
        boolean removed = false;

        if (Files.exists(cpPath)) {
            try {
                Files.delete(cpPath);
                removed = true;
            } catch (IOException e) {
                return false;
            }
        }

        List<CheckpointMeta> items = new ArrayList<>(list(rootDir));
        int originalSize = items.size();
        items.removeIf(m -> m.id().equals(id));
        if (items.size() != originalSize) {
            writeIndex(rootDir, items);
            removed = true;
        }

        return removed;
    }

    private static Path indexPath(String rootDir) {
        String safeName = PathUtils.sanitizeForFilename(Paths.get(rootDir).toAbsolutePath().normalize().toString());
        return PathUtils.getSessionsDir().resolve(safeName).resolve("checkpoints").resolve("index.json");
    }

    private static Path snapshotPath(String rootDir, String id) {
        String safeName = PathUtils.sanitizeForFilename(Paths.get(rootDir).toAbsolutePath().normalize().toString());
        return PathUtils.getSessionsDir().resolve(safeName).resolve("checkpoints").resolve(id + ".json");
    }

    private static void writeIndex(String rootDir, List<CheckpointMeta> items) {
        try {
            Path path = indexPath(rootDir);
            Files.createDirectories(path.getParent());
            Files.writeString(path, JsonUtils.toPrettyJson(items));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write checkpoint index", e);
        }
    }
}
