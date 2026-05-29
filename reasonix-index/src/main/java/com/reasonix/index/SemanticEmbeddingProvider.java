package com.reasonix.index;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface SemanticEmbeddingProvider {

    float[] embed(String text) throws IOException;

    List<float[]> embedBatch(List<String> texts) throws IOException;

    int embeddingDimension();

    String providerName();

    record OllamaConfig(String baseUrl, String model) {
        public static final String DEFAULT_BASE_URL = "http://localhost:11434";
        public static final String DEFAULT_MODEL = "nomic-embed-text";
    }
}