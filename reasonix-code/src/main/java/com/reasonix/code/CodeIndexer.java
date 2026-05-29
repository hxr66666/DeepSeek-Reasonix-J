package com.reasonix.code;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CodeIndexer {

    private static final Logger log = LoggerFactory.getLogger(CodeIndexer.class);

    private static final Set<String> INDEXED_EXTENSIONS = Set.of(
            ".java", ".kt", ".py", ".js", ".ts", ".tsx", ".jsx", ".go", ".rs",
            ".c", ".cpp", ".h", ".hpp", ".cs", ".rb", ".php", ".swift", ".scala",
            ".xml", ".yaml", ".yml", ".json", ".toml", ".properties", ".md", ".txt",
            ".sql", ".sh", ".bash", ".zsh", ".html", ".css", ".scss"
    );

    private static final Set<String> IGNORED_DIRS = Set.of(
            "node_modules", ".git", ".svn", ".hg", "target", "build", "dist",
            "out", ".idea", ".vscode", "__pycache__", ".gradle", ".mvn",
            "vendor", "Pods", ".next", ".nuxt", "coverage"
    );

    private final Path workspaceRoot;
    private final Map<String, FileInfo> index = new ConcurrentHashMap<>();
    private final Map<String, List<SymbolExtractor.Symbol>> symbolIndex = new ConcurrentHashMap<>();
    private final SymbolExtractor symbolExtractor = new SymbolExtractor();

    public CodeIndexer(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public void indexWorkspace() throws IOException {
        index.clear();
        symbolIndex.clear();
        Files.walkFileTree(workspaceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (IGNORED_DIRS.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                int dotIdx = name.lastIndexOf('.');
                if (dotIdx > 0 && INDEXED_EXTENSIONS.contains(name.substring(dotIdx))) {
                    try {
                        String relativePath = workspaceRoot.relativize(file).toString();
                        FileInfo info = new FileInfo(
                                file,
                                attrs.size(),
                                attrs.lastModifiedTime().toMillis(),
                                name.substring(dotIdx)
                        );
                        index.put(relativePath, info);

                        if (".java".equals(name.substring(dotIdx))) {
                            List<SymbolExtractor.Symbol> symbols = symbolExtractor.extractSymbols(file);
                            if (!symbols.isEmpty()) {
                                symbolIndex.put(relativePath, symbols);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                return FileVisitResult.CONTINUE;
            }
        });
        log.info("Indexed {} files, {} with symbols in {}", index.size(), symbolIndex.size(), workspaceRoot);
    }

    public List<FileInfo> search(String query) {
        String lowerQuery = query.toLowerCase();
        return index.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().contains(lowerQuery))
                .map(Map.Entry::getValue)
                .limit(50)
                .toList();
    }

    public Optional<FileInfo> getFile(String relativePath) {
        return Optional.ofNullable(index.get(relativePath));
    }

    public List<FileInfo> getByExtension(String extension) {
        return index.values().stream()
                .filter(f -> f.extension().equals(extension))
                .toList();
    }

    public List<SymbolExtractor.Symbol> searchSymbols(String nameQuery) {
        String lowerQuery = nameQuery.toLowerCase();
        List<SymbolExtractor.Symbol> results = new ArrayList<>();

        for (var entry : symbolIndex.entrySet()) {
            for (var symbol : entry.getValue()) {
                if (symbol.name().toLowerCase().contains(lowerQuery)) {
                    results.add(symbol);
                }
            }
        }

        return results.stream().limit(100).toList();
    }

    public List<SymbolExtractor.Symbol> getSymbols(String relativePath) {
        return symbolIndex.getOrDefault(relativePath, List.of());
    }

    public Map<String, List<SymbolExtractor.Symbol>> getSymbolIndex() {
        return Collections.unmodifiableMap(symbolIndex);
    }

    public int size() {
        return index.size();
    }

    public int symbolCount() {
        return symbolIndex.values().stream().mapToInt(List::size).sum();
    }

    public record FileInfo(Path path, long size, long lastModified, String extension) {}
}
