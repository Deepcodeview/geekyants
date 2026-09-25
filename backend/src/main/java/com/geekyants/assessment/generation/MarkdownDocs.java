package com.geekyants.assessment.generation;

import com.geekyants.assessment.blueprint.BlueprintContent;
import com.geekyants.assessment.blueprint.BlueprintContent.Requirement;
import com.geekyants.assessment.generation.ProjectGenerator.Features;
import com.geekyants.assessment.generation.ProjectGenerator.GenerationInput;
import java.util.List;

/** Renders repository documentation from the approved blueprint. */
final class MarkdownDocs {

    private final GenerationInput input;
    private final BlueprintContent bp;
    private final Features features;

    MarkdownDocs(GenerationInput input, Features features) {
        this.input = input;
        this.bp = input.blueprint();
        this.features = features;
    }

    String readme() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(bp.projectName()).append("\n\n");
        sb.append("| | |\n|---|---|\n");
        sb.append("| Assignment | `").append(input.assignmentId()).append("` |\n");
        sb.append("| Project | `").append(input.projectId()).append("` |\n");
        sb.append("| Blueprint | `").append(input.blueprintId()).append("` |\n");
        sb.append("| Domain | ").append(cell(input.config().domain())).append(" |\n");
        sb.append("| Complexity | ").append(input.config().complexity()).append(" |\n");
        sb.append("| Time allowed | ").append(input.config().durationDays()).append(" days from access |\n");
        sb.append("| Technology | ").append(cell(String.join(", ", input.config().technologies()))).append(" |\n\n");

        sb.append("## The problem\n\n").append(bp.businessProblem()).append("\n\n");
        sb.append("## Scope\n\n").append(bp.scope()).append("\n\n");
        sb.append("## What to do\n\n").append(bp.candidateInstructions()).append("\n\n");

        sb.append("## Requirements\n\n");
        sb.append("| ID | Requirement | Mandatory |\n|---|---|---|\n");
        for (Requirement r : bp.requirements()) {
            sb.append("| ").append(r.code()).append(" | ").append(cell(r.title())).append(" | ")
                    .append(r.mandatory() ? "Yes" : "Optional").append(" |\n");
        }
        sb.append("\nFull details and acceptance criteria: [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md).\n\n");

        sb.append("## Getting started\n\nYou need JDK 21, Maven 3.9+ and Docker.\n\n```bash\n");
        if (features.database() || features.kafka()) {
            sb.append("# Start local dependencies (");
            sb.append(String.join(", ", deps())).append(")\ndocker compose up -d\n\n");
        }
        sb.append("# Run the application on http://localhost:8080\nmvn spring-boot:run\n\n");
        sb.append("# Run unit tests (*Test) and integration tests (*IT)\nmvn verify\n```\n\n");
        sb.append("Health check: `GET http://localhost:8080/actuator/health`\n\n");

        sb.append("""
                ## How submission works

                1. Commit and push your work to the `main` branch of this repository. Every push runs the
                   feedback build in `.github/workflows/ci.yml`; you can see the result on your dashboard.
                2. When you are done, click **Submit Project** on your dashboard. The platform records the exact
                   commit at the head of `main`, tags it, and switches this repository to read-only.
                3. That commit is built, tested and deployed in a separate evaluation environment. Your local
                   machine and the feedback build do not decide the result.
                4. You then take a short multiple-choice assessment about your own implementation.

                If you have not submitted when the time allowed runs out, the head of `main` at that moment is
                submitted automatically.

                ## Rules

                - Keep `/actuator/health` working and keep the `Dockerfile` building: the evaluation deploys it.
                - Read configuration from the standard Spring properties so the evaluation environment can
                  point the application at its own database and broker.
                - Do not commit secrets. Force-pushes to `main` are blocked.
                - The evaluation also runs tests that are not in this repository.

                ## Documentation

                - [Requirements](docs/REQUIREMENTS.md)
                - [API](docs/API.md)
                - [Architecture](docs/ARCHITECTURE.md)
                """);
        if (features.database()) {
            sb.append("- [Database](docs/DATABASE.md)\n");
        }
        if (features.kafka() || !bp.events().isEmpty()) {
            sb.append("- [Messaging](docs/KAFKA.md)\n");
        }
        sb.append("- [Evaluation](docs/EVALUATION.md)\n");
        return sb.toString();
    }

    String requirements() {
        StringBuilder sb = new StringBuilder("# Requirements\n\n");
        for (Requirement r : bp.requirements()) {
            sb.append("## ").append(r.code()).append(" — ").append(r.title()).append("\n\n");
            sb.append("*").append(r.mandatory() ? "Mandatory" : "Optional").append(" · ")
                    .append(r.category()).append("*\n\n");
            sb.append(r.description()).append("\n\n**Acceptance criteria**\n\n");
            for (String c : r.acceptanceCriteria()) {
                sb.append("- ").append(c).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    String api() {
        StringBuilder sb = new StringBuilder("# API\n\n");
        if (bp.apis().isEmpty()) {
            return sb.append("No HTTP API is specified for this project.\n").toString();
        }
        sb.append("| Method | Path | Description | Requirements |\n|---|---|---|---|\n");
        for (BlueprintContent.ApiEndpoint api : bp.apis()) {
            sb.append("| ").append(api.method()).append(" | `").append(api.path()).append("` | ")
                    .append(cell(api.description())).append(" | ").append(refs(api.requirementCodes())).append(" |\n");
        }
        return sb.toString();
    }

    String architecture() {
        StringBuilder sb = new StringBuilder("# Architecture\n\n## Modules\n\n");
        for (BlueprintContent.Module m : bp.modules()) {
            sb.append("- **").append(m.name()).append("**: ").append(m.description()).append("\n");
        }
        section(sb, "Non-functional requirements", bp.nonFunctionalRequirements());
        section(sb, "Security requirements", bp.securityRequirements());
        return sb.toString();
    }

    String database() {
        StringBuilder sb = new StringBuilder("# Database\n\nMySQL. The model below is the minimum; "
                + "you may add tables and columns.\n\n");
        for (BlueprintContent.DataEntity e : bp.dataModel()) {
            sb.append("## ").append(e.name()).append("\n\n");
            if (e.description() != null) {
                sb.append(e.description()).append("\n\n");
            }
            if (e.fields() != null) {
                for (String f : e.fields()) {
                    sb.append("- ").append(f).append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    String messaging() {
        StringBuilder sb = new StringBuilder("# Messaging\n\n");
        if (bp.events().isEmpty()) {
            return sb.append("No events are specified for this project.\n").toString();
        }
        sb.append("| Event | Topic | Producer | Consumer | Requirements |\n|---|---|---|---|---|\n");
        for (BlueprintContent.MessagingEvent e : bp.events()) {
            sb.append("| ").append(cell(e.name())).append(" | `").append(e.topic()).append("` | ")
                    .append(cell(e.producer())).append(" | ").append(cell(e.consumer())).append(" | ")
                    .append(refs(e.requirementCodes())).append(" |\n");
        }
        sb.append("\n## Payloads\n\n");
        for (BlueprintContent.MessagingEvent e : bp.events()) {
            sb.append("### ").append(e.name()).append("\n\n").append(e.payload() == null ? "" : e.payload())
                    .append("\n\n");
        }
        return sb.toString();
    }

    String evaluation() {
        StringBuilder sb = new StringBuilder("# Evaluation\n\n");
        sb.append("Your submitted commit is built, tested and deployed in an isolated environment, then "
                + "analysed against the requirements. Afterwards you answer 12–15 questions about your "
                + "implementation.\n");
        section(sb, "What reviewers look for", bp.evaluationCriteria());
        section(sb, "Testing expectations", bp.testingRequirements());
        return sb.toString();
    }

    private List<String> deps() {
        return java.util.stream.Stream.of(features.database() ? "MySQL" : null, features.kafka() ? "Kafka" : null)
                .filter(java.util.Objects::nonNull).toList();
    }

    private static void section(StringBuilder sb, String title, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        sb.append("\n## ").append(title).append("\n\n");
        for (String item : items) {
            sb.append("- ").append(item).append("\n");
        }
    }

    private static String refs(List<String> codes) {
        return codes == null || codes.isEmpty() ? "" : String.join(", ", codes);
    }

    private static String cell(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
    }
}
