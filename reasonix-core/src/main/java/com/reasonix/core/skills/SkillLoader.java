package com.reasonix.core.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    private final List<Path> skillPaths;

    public SkillLoader(List<Path> skillPaths) {
        this.skillPaths = skillPaths;
    }

    public List<Skill> loadAll() {
        List<Skill> skills = new ArrayList<>();
        for (Path dir : skillPaths) {
            if (!Files.isDirectory(dir)) continue;
            skills.addAll(loadFromDirectory(dir));
        }
        return skills;
    }

    public Skill loadByName(String name) {
        for (Path dir : skillPaths) {
            if (!Files.isDirectory(dir)) continue;
            Path skillFile = dir.resolve(name + ".md");
            if (Files.exists(skillFile)) {
                return parseSkillFile(skillFile);
            }
        }
        return null;
    }

    private List<Skill> loadFromDirectory(Path dir) {
        List<Skill> skills = new ArrayList<>();
        try {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".md"))
                      .map(this::parseSkillFile)
                      .filter(s -> s != null)
                      .forEach(skills::add);
            }
        } catch (IOException e) {
            log.error("Failed to load skills from {}: {}", dir, e.getMessage());
        }
        return skills;
    }

    private Skill parseSkillFile(Path file) {
        try {
            String content = Files.readString(file);
            String name = file.getFileName().toString().replace(".md", "");
            String mode = content.contains("mode: subagent") ? "subagent" : "inline";
            return new Skill(name, content, mode, file);
        } catch (IOException e) {
            log.error("Failed to parse skill file {}: {}", file, e.getMessage());
            return null;
        }
    }

    public record Skill(String name, String content, String mode, Path filePath) {}
}
