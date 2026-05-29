package com.reasonix.core.transcript;

import java.util.List;

public record TranscriptDiff(
        List<DiffEntry> entries
) {
    public record DiffEntry(
            String path,
            DiffType type,
            List<String> removedLines,
            List<String> addedLines,
            int startLine
    ) {}

    public enum DiffType { ADD, REMOVE, MODIFY }
}
