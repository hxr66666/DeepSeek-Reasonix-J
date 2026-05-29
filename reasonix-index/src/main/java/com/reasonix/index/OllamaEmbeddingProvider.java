package com.reasonix.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OllamaEmbeddingProvider implements SemanticEmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingProvider.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 180;
    private static final int BATCH_SIZE = 10;

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaEmbeddingProvider(SemanticEmbeddingProvider.OllamaConfig config) {
        this.baseUrl = config.baseUrl() != null ? config.baseUrl() : SemanticEmbeddingProvider.OllamaConfig.DEFAULT_BASE_URL;
        this.model = config.model() != null ? config.model() : SemanticEmbeddingProvider.OllamaConfig.DEFAULT_MODEL;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public float[] embed(String text) throws IOException {
        try {
            String requestBody = """
                {
                    "model": "%s",
                    "prompt": %s
                }
                """.formatted(model, toJson(text));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embeddings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Ollama returned " + response.statusCode() + ": " + response.body());
            }

            Map<String, Object> json = parseJson(response.body());
            return parseEmbedding(json);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Embedding request interrupted", e);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) throws IOException {
        List<float[]> results = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, texts.size());
            for (int j = i; j < end; j++) {
                try {
                    results.add(embed(texts.get(j)));
                } catch (IOException e) {
                    log.debug("Failed to embed text at index {}: {}", j, e.getMessage());
                    results.add(null);
                }
            }
        }
        return results;
    }

    @Override
    public int embeddingDimension() {
        return 768;
    }

    @Override
    public String providerName() {
        return "ollama";
    }

    private String toJson(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else sb.append(c);
        }
        return "\"" + sb + "\"";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) throws IOException {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IOException("Failed to parse JSON response: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private float[] parseEmbedding(Map<String, Object> json) {
        Object embeddingObj = json.get("embedding");
        if (embeddingObj == null) {
            throw new IllegalStateException("Ollama response missing 'embedding' field");
        }
        if (embeddingObj instanceof List) {
            List<Number> list = (List<Number>) embeddingObj;
            float[] result = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                result[i] = list.get(i).floatValue();
            }
            return result;
        }
        throw new IllegalStateException("Embedding is not a list: " + embeddingObj.getClass());
    }
}