package com.reasonix.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Config {

    private static final Logger log = LoggerFactory.getLogger(Config.class);
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private final Map<String, Object> data;
    private final Path configPath;

    public Config() {
        this.data = new HashMap<>();
        this.configPath = null;
    }

    public Config(Path configPath) {
        this.configPath = configPath;
        this.data = loadConfig(configPath);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadConfig(Path path) {
        if (path == null || !Files.exists(path)) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> loaded = YAML_MAPPER.readValue(path.toFile(), Map.class);
            return loaded != null ? loaded : new HashMap<>();
        } catch (IOException e) {
            log.warn("Failed to load config from {}: {}", path, e.getMessage());
            return new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = getNestedValue(key);
        if (value == null) return null;
        if (type.isInstance(value)) return (T) value;
        return convert(value, type);
    }

    public <T> T get(String key, Class<T> type, T defaultValue) {
        T value = get(key, type);
        return value != null ? value : defaultValue;
    }

    public String getString(String key) {
        return get(key, String.class);
    }

    public String getString(String key, String defaultValue) {
        return get(key, String.class, defaultValue);
    }

    public Integer getInteger(String key) {
        return get(key, Integer.class);
    }

    public Integer getInteger(String key, Integer defaultValue) {
        return get(key, Integer.class, defaultValue);
    }

    public Boolean getBoolean(String key) {
        return get(key, Boolean.class);
    }

    public Boolean getBoolean(String key, Boolean defaultValue) {
        return get(key, Boolean.class, defaultValue);
    }

    public Double getDouble(String key) {
        return get(key, Double.class);
    }

    public Double getDouble(String key, Double defaultValue) {
        return get(key, Double.class, defaultValue);
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
        Object value = getNestedValue(key);
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return Collections.emptyList();
    }

    public void set(String key, Object value) {
        setNestedValue(key, value);
    }

    public boolean has(String key) {
        return getNestedValue(key) != null;
    }

    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(data);
    }

    public Config merge(Config other) {
        Map<String, Object> merged = new HashMap<>(this.data);
        merged.putAll(other.data);
        Config result = new Config();
        result.data.putAll(merged);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object getNestedValue(String key) {
        String[] parts = key.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private void setNestedValue(String key, Object value) {
        String[] parts = key.split("\\.");
        Map<String, Object> current = data;
        for (int i = 0; i < parts.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(parts[i], k -> new HashMap<>());
        }
        current.put(parts[parts.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private <T> T convert(Object value, Class<T> type) {
        if (type == String.class) return (T) String.valueOf(value);
        if (type == Integer.class && value instanceof Number n) return (T) (Integer) n.intValue();
        if (type == Long.class && value instanceof Number n) return (T) (Long) n.longValue();
        if (type == Double.class && value instanceof Number n) return (T) (Double) n.doubleValue();
        if (type == Boolean.class) return (T) Boolean.valueOf(String.valueOf(value));
        return null;
    }
}
