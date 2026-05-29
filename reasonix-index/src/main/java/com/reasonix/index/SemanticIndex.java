package com.reasonix.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SemanticIndex {

    private static final Logger log = LoggerFactory.getLogger(SemanticIndex.class);

    private final Map<String, DocumentChunk> chunks = new ConcurrentHashMap<>();
    private final Map<String, float[]> embeddings = new ConcurrentHashMap<>();
    private final Path workspaceRoot;
    private SemanticEmbeddingProvider embeddingProvider;
    private boolean useRealEmbedding = false;

    public SemanticIndex(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public void setEmbeddingProvider(SemanticEmbeddingProvider provider) {
        this.embeddingProvider = provider;
        this.useRealEmbedding = true;
    }

    public void setEmbeddingProvider(SemanticEmbeddingProvider.OllamaConfig config) {
        setEmbeddingProvider(new OllamaEmbeddingProvider(config));
    }

    public void buildIndex() throws IOException {
        chunks.clear();
        embeddings.clear();

        if (useRealEmbedding && embeddingProvider != null) {
            buildIndexWithRealEmbedding();
        } else {
            buildIndexWithSimpleEmbedding();
        }
        log.info("Built semantic index with {} chunks", chunks.size());
    }

    private void buildIndexWithRealEmbedding() throws IOException {
        List<String> chunkContents = new ArrayList<>();
        List<String> chunkIds = new ArrayList<>();

        Files.walkFileTree(workspaceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (isTextFile(name)) {
                    try {
                        String content = Files.readString(file);
                        chunkDocument(workspaceRoot.relativize(file).toString(), content, chunkIds, chunkContents);
                    } catch (IOException ignored) {}
                }
                return FileVisitResult.CONTINUE;
            }
        });

        List<float[]> embedded = embeddingProvider.embedBatch(chunkContents);
        for (int i = 0; i < chunkIds.size(); i++) {
            String chunkId = chunkIds.get(i);
            float[] embedding = embedded.get(i);
            if (embedding != null) {
                embeddings.put(chunkId, embedding);
            }
        }
    }

    private void buildIndexWithSimpleEmbedding() throws IOException {
        Files.walkFileTree(workspaceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (isTextFile(name)) {
                    try {
                        String content = Files.readString(file);
                        chunkDocumentSimple(workspaceRoot.relativize(file).toString(), content);
                    } catch (IOException ignored) {}
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public List<SearchResult> search(String query, int topK) {
        if (chunks.isEmpty()) return List.of();

        float[] queryEmbedding;
        if (useRealEmbedding && embeddingProvider != null) {
            try {
                queryEmbedding = embeddingProvider.embed(query);
            } catch (IOException e) {
                log.warn("Failed to get embedding from provider, using simple embedding", e);
                queryEmbedding = computeSimpleEmbedding(query);
            }
        } else {
            queryEmbedding = computeSimpleEmbedding(query);
        }

        List<SearchResult> results = new ArrayList<>();
        for (var entry : embeddings.entrySet()) {
            float similarity = cosineSimilarity(queryEmbedding, entry.getValue());
            DocumentChunk chunk = chunks.get(entry.getKey());
            results.add(new SearchResult(entry.getKey(), chunk.relativePath(), chunk.content(), similarity));
        }

        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        return results.subList(0, Math.min(topK, results.size()));
    }

    private void chunkDocument(String relativePath, String content, List<String> chunkIds, List<String> chunkContents) {
        String[] lines = content.split("\n");
        int chunkSize = 50;
        int overlap = 10;

        for (int i = 0; i < lines.length; i += (chunkSize - overlap)) {
            int end = Math.min(i + chunkSize, lines.length);
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < end; j++) {
                sb.append(lines[j]).append("\n");
            }

            String chunkId = relativePath + ":" + i + "-" + end;
            String chunkContent = sb.toString();

            chunks.put(chunkId, new DocumentChunk(relativePath, i, end, chunkContent));
            chunkIds.add(chunkId);
            chunkContents.add(chunkContent);
        }
    }

    private void chunkDocumentSimple(String relativePath, String content) {
        String[] lines = content.split("\n");
        int chunkSize = 50;
        int overlap = 10;

        for (int i = 0; i < lines.length; i += (chunkSize - overlap)) {
            int end = Math.min(i + chunkSize, lines.length);
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < end; j++) {
                sb.append(lines[j]).append("\n");
            }

            String chunkId = relativePath + ":" + i + "-" + end;
            String chunkContent = sb.toString();

            chunks.put(chunkId, new DocumentChunk(relativePath, i, end, chunkContent));
            embeddings.put(chunkId, computeSimpleEmbedding(chunkContent));
        }
    }

    private float[] computeSimpleEmbedding(String text) {
        int dim = 128;
        float[] embedding = new float[dim];
        String lower = text.toLowerCase();

        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            embedding[c % dim] += 1.0f;
            if (i > 0) {
                embedding[(c + lower.charAt(i - 1)) % dim] += 0.5f;
            }
        }

        float norm = 0;
        for (float v : embedding) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) embedding[i] /= norm;
        }

        return embedding;
    }

    private float cosineSimilarity(float[] a, float[] b) {
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        float denom = (float) (Math.sqrt(normA) * Math.sqrt(normB));
        return denom > 0 ? dot / denom : 0;
    }

    private boolean isTextFile(String name) {
        return name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".py")
                || name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".md")
                || name.endsWith(".txt") || name.endsWith(".xml") || name.endsWith(".yaml")
                || name.endsWith(".yml") || name.endsWith(".json") || name.endsWith(".toml")
                || name.endsWith(".properties") || name.endsWith(".sql") || name.endsWith(".sh");
    }

    public record DocumentChunk(String relativePath, int startLine, int endLine, String content) {}
    public record SearchResult(String chunkId, String relativePath, String content, float score) {}
}