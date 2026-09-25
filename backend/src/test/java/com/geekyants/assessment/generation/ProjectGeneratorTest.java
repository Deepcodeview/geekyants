package com.geekyants.assessment.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.geekyants.assessment.Fixtures;
import com.geekyants.assessment.github.GitHubClient.FileToCommit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProjectGeneratorTest {

    private final ProjectGenerator generator = new ProjectGenerator();

    @Test
    void rendersTemplateInfrastructureAndDocs() throws IOException {
        ProjectGenerator.GeneratedProject project = generator.generate(new ProjectGenerator.GenerationInput(
                "ASSIGN-1", "PROJ-1", "BP-1", Fixtures.config(), Fixtures.blueprint()));
        Map<String, String> files = project.files().stream()
                .collect(Collectors.toMap(FileToCommit::path, FileToCommit::content));

        assertThat(files).containsKeys("pom.xml", "Dockerfile", ".gitignore", ".dockerignore",
                ".github/workflows/ci.yml", "docker-compose.yml", ".env.example", "README.md",
                "src/main/java/com/assessment/app/Application.java", "docs/REQUIREMENTS.md", "docs/KAFKA.md",
                "docs/DATABASE.md");
        assertThat(files.values()).noneMatch(c -> c.contains("{{"));
        assertThat(files.get("pom.xml")).contains("spring-boot-starter-kafka", "mysql-connector-j",
                "<artifactId>banking-transaction-service</artifactId>");
        assertThat(files.get("docker-compose.yml")).contains("mysql:", "kafka:");
        assertThat(files.get("README.md")).contains("ASSIGN-1", "REQ-003");
        assertThat(files.get(".github/workflows/ci.yml")).doesNotContain("secrets.");

        // Set GENERATED_PROJECT_DIR to write the project out and build it: mvn -f $DIR verify
        String out = System.getenv("GENERATED_PROJECT_DIR");
        if (out != null) {
            for (FileToCommit f : project.files()) {
                Path p = Path.of(out, f.path());
                Files.createDirectories(p.getParent());
                Files.writeString(p, f.content());
            }
        }
    }

    @Test
    void omitsInfrastructureTheStackDoesNotUse() {
        var config = new com.geekyants.assessment.blueprint.ProjectConfiguration("Retail", "Catalog",
                com.geekyants.assessment.analysis.RequirementAnalysis.Complexity.BASIC, 3,
                java.util.List.of("Java", "Spring Boot"), java.util.List.of(), java.util.List.of(), null);
        var bp = Fixtures.blueprint();
        var noEvents = new com.geekyants.assessment.blueprint.BlueprintContent(bp.projectName(), bp.businessProblem(),
                bp.scope(), bp.modules(), bp.requirements(), bp.apis(), bp.dataModel(), java.util.List.of(),
                bp.nonFunctionalRequirements(), bp.securityRequirements(), bp.testingRequirements(),
                bp.evaluationCriteria(), bp.candidateInstructions());
        var files = generator.generate(new ProjectGenerator.GenerationInput("A", "P", "B", config, noEvents)).files();
        assertThat(files).extracting(FileToCommit::path)
                .doesNotContain("docker-compose.yml", "docs/KAFKA.md", "docs/DATABASE.md");
    }
}
