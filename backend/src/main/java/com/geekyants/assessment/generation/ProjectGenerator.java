package com.geekyants.assessment.generation;

import com.geekyants.assessment.blueprint.BlueprintContent;
import com.geekyants.assessment.blueprint.ProjectConfiguration;
import com.geekyants.assessment.github.GitHubClient.FileToCommit;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Renders the candidate repository: a vetted template for the stack, infrastructure for the chosen
 * technologies, and documentation generated from the approved blueprint. Deterministic, so the same
 * blueprint always yields the same repository.
 */
@Component
public class ProjectGenerator {

    private static final String TEMPLATE_ROOT = "templates/";

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public record GenerationInput(
            String assignmentId,
            String projectId,
            String blueprintId,
            ProjectConfiguration config,
            BlueprintContent blueprint) {
    }

    public record GeneratedProject(String templateKey, List<FileToCommit> files) {
    }

    record Features(boolean database, boolean kafka) {

        static Features from(List<String> technologies) {
            boolean database = TemplateCatalog.has(technologies, "mysql")
                    || TemplateCatalog.has(technologies, "jpa")
                    || TemplateCatalog.has(technologies, "hibernate");
            return new Features(database, TemplateCatalog.has(technologies, "kafka"));
        }
    }

    public GeneratedProject generate(GenerationInput input) {
        String templateKey = TemplateCatalog.select(input.config().technologies())
                .orElseThrow(() -> new IllegalArgumentException("No template for " + input.config().technologies()));
        Features features = Features.from(input.config().technologies());
        Map<String, String> vars = Map.of(
                "ARTIFACT_ID", artifactId(input.blueprint().projectName()),
                "PROJECT_NAME", input.blueprint().projectName(),
                "EXTRA_DEPENDENCIES", extraDependencies(features),
                "EXTRA_TEST_DEPENDENCIES", extraTestDependencies(features),
                "SPRING_CONFIG", springConfig(features));

        List<FileToCommit> files = new ArrayList<>(renderTemplate(templateKey, vars));
        if (features.database() || features.kafka()) {
            files.add(new FileToCommit("docker-compose.yml", dockerCompose(features)));
            files.add(new FileToCommit(".env.example", envExample(features)));
        }
        MarkdownDocs docs = new MarkdownDocs(input, features);
        files.add(new FileToCommit("README.md", docs.readme()));
        files.add(new FileToCommit("docs/REQUIREMENTS.md", docs.requirements()));
        files.add(new FileToCommit("docs/API.md", docs.api()));
        files.add(new FileToCommit("docs/ARCHITECTURE.md", docs.architecture()));
        files.add(new FileToCommit("docs/EVALUATION.md", docs.evaluation()));
        if (features.database()) {
            files.add(new FileToCommit("docs/DATABASE.md", docs.database()));
        }
        if (features.kafka() || !input.blueprint().events().isEmpty()) {
            files.add(new FileToCommit("docs/KAFKA.md", docs.messaging()));
        }
        files.sort(Comparator.comparing(FileToCommit::path));
        return new GeneratedProject(templateKey, files);
    }

    private List<FileToCommit> renderTemplate(String templateKey, Map<String, String> vars) {
        String base = TEMPLATE_ROOT + templateKey + "/";
        try {
            Resource root = resolver.getResource("classpath:" + base);
            String rootUrl = root.getURL().toString();
            List<FileToCommit> files = new ArrayList<>();
            for (Resource resource : resolver.getResources("classpath*:" + base + "**/*")) {
                if (!resource.isReadable() || resource.getURL().toString().endsWith("/")) {
                    continue;
                }
                String relative = resource.getURL().toString().substring(rootUrl.length());
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                for (Map.Entry<String, String> v : vars.entrySet()) {
                    content = content.replace("{{" + v.getKey() + "}}", v.getValue());
                }
                files.add(new FileToCommit(dotfilePath(relative), content));
            }
            if (files.isEmpty()) {
                throw new IllegalStateException("Template " + templateKey + " is empty");
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read template " + templateKey, e);
        }
    }

    /** Maven drops dotfiles from resources, so templates store ".gitignore" as "_gitignore". */
    static String dotfilePath(String relative) {
        String[] parts = relative.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].startsWith("_")) {
                parts[i] = "." + parts[i].substring(1);
            }
        }
        return String.join("/", parts);
    }

    static String artifactId(String projectName) {
        String slug = projectName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "assessment-project" : slug;
    }

    private static String extraDependencies(Features f) {
        StringBuilder sb = new StringBuilder();
        if (f.database()) {
            sb.append(dependency("org.springframework.boot", "spring-boot-starter-data-jpa", null));
            sb.append(dependency("com.mysql", "mysql-connector-j", "runtime"));
        }
        if (f.kafka()) {
            sb.append(dependency("org.springframework.boot", "spring-boot-starter-kafka", null));
        }
        return sb.toString().stripTrailing();
    }

    private static String extraTestDependencies(Features f) {
        StringBuilder sb = new StringBuilder();
        if (f.database()) {
            sb.append(dependency("org.testcontainers", "testcontainers-mysql", "test"));
        }
        if (f.kafka()) {
            sb.append(dependency("org.springframework.boot", "spring-boot-starter-kafka-test", "test"));
            sb.append(dependency("org.testcontainers", "testcontainers-kafka", "test"));
        }
        return sb.toString().stripTrailing();
    }

    private static String dependency(String group, String artifact, String scope) {
        return """
                        <dependency>
                            <groupId>%s</groupId>
                            <artifactId>%s</artifactId>%s
                        </dependency>
                """.formatted(group, artifact, scope == null ? "" : "\n            <scope>" + scope + "</scope>");
    }

    private static String springConfig(Features f) {
        StringBuilder sb = new StringBuilder();
        if (f.database()) {
            sb.append("""
                      # Local defaults match docker-compose.yml. The evaluation environment overrides them
                      # through SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD.
                      datasource:
                        url: jdbc:mysql://localhost:3306/app
                        username: app
                        password: app
                      jpa:
                        open-in-view: false
                        hibernate:
                          ddl-auto: update
                    """);
        }
        if (f.kafka()) {
            sb.append("""
                      # Overridden in evaluation through SPRING_KAFKA_BOOTSTRAP_SERVERS.
                      kafka:
                        bootstrap-servers: localhost:9092
                    """);
        }
        return sb.toString().stripTrailing();
    }

    private static String dockerCompose(Features f) {
        StringBuilder sb = new StringBuilder("# Local development dependencies. Start with: docker compose up -d\nservices:\n");
        if (f.database()) {
            sb.append("""
                      mysql:
                        image: mysql:8.4
                        environment:
                          MYSQL_DATABASE: app
                          MYSQL_USER: app
                          MYSQL_PASSWORD: ${MYSQL_PASSWORD:-app}
                          MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root}
                        ports:
                          - "3306:3306"
                        healthcheck:
                          test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
                          interval: 5s
                          retries: 20
                    """);
        }
        if (f.kafka()) {
            sb.append("""
                      kafka:
                        image: apache/kafka:3.9.1
                        ports:
                          - "9092:9092"
                    """);
        }
        return sb.toString();
    }

    private static String envExample(Features f) {
        StringBuilder sb = new StringBuilder("# Local-only values for docker-compose.yml. Copy to .env to change them.\n");
        if (f.database()) {
            sb.append("MYSQL_PASSWORD=app\nMYSQL_ROOT_PASSWORD=root\n");
        }
        return sb.toString();
    }
}
