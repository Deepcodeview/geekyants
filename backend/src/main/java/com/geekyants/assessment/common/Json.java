package com.geekyants.assessment.common;

import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Thin wrapper so entities can keep JSON documents as text columns. */
@Component
public class Json {

    private final ObjectMapper mapper;

    public Json(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        return value == null ? null : mapper.writeValueAsString(value);
    }

    public <T> T read(String json, Class<T> type) {
        return json == null ? null : mapper.readValue(json, type);
    }

    public <T> T read(String json, TypeReference<T> type) {
        return json == null ? null : mapper.readValue(json, type);
    }
}
