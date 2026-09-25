package com.geekyants.assessment.llm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads system prompts from {@code classpath:prompts/<name>.md}. */
@Component
public class PromptTemplates {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String system(String name) {
        return cache.computeIfAbsent(name, n -> {
            try {
                return new ClassPathResource("prompts/" + n + ".md").getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Missing prompt " + n, e);
            }
        });
    }
}
