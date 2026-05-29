package com.reasonix.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;

public final class JsonUtils {

    private JsonUtils() {}

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.INDENT_OUTPUT, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private static final ObjectMapper PRETTY_MAPPER = MAPPER.copy()
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    public static String toPrettyJson(Object obj) {
        try {
            return PRETTY_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    public static <T> T parse(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON deserialization failed for " + type.getSimpleName(), e);
        }
    }

    public static <T> T parse(String json, TypeReference<T> typeRef) {
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseToMap(String json) {
        return parse(json, new TypeReference<Map<String, Object>>() {});
    }

    public static boolean isValidJson(String json) {
        try {
            MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
