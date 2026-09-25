package com.geekyants.assessment.generation;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Project skeletons are rendered from vetted templates rather than written by the LLM, so every
 * candidate starts from a build that is known to compile. Add a stack by adding a template.
 */
public final class TemplateCatalog {

    public static final String JAVA_SPRING_BOOT = "java-spring-boot";

    private TemplateCatalog() {
    }

    public static Optional<String> select(List<String> technologies) {
        if (has(technologies, "java") && has(technologies, "spring")) {
            return Optional.of(JAVA_SPRING_BOOT);
        }
        return Optional.empty();
    }

    public static boolean has(List<String> technologies, String keyword) {
        return technologies.stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(keyword));
    }
}
