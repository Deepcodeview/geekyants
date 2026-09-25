package com.geekyants.assessment;

import com.geekyants.assessment.analysis.RequirementAnalysis;
import com.geekyants.assessment.analysis.RequirementAnalysis.Complexity;
import com.geekyants.assessment.analysis.RequirementAnalysis.Level;
import com.geekyants.assessment.blueprint.BlueprintContent;
import com.geekyants.assessment.blueprint.BlueprintContent.Category;
import com.geekyants.assessment.blueprint.ProjectConfiguration;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult.CoverageStatus;
import com.geekyants.assessment.mcq.GeneratedMcqs;
import com.geekyants.assessment.mcq.McqCategory;
import java.util.ArrayList;
import java.util.List;

public final class Fixtures {

    private Fixtures() {
    }

    public static RequirementAnalysis analysis() {
        return new RequirementAnalysis("Strong Java backend candidate, limited Kafka.", "Banking",
                List.of(new RequirementAnalysis.SkillAssessment("Kafka", true, Level.INTERMEDIATE, Level.BEGINNER,
                        "one side project")),
                List.of("Kafka"), List.of("Java", "Spring Boot", "MySQL", "Kafka"), Complexity.ADVANCED,
                List.of("publish domain events"), List.of(new RequirementAnalysis.ProjectIdea("Ledger", "fits")));
    }

    public static ProjectConfiguration config() {
        return new ProjectConfiguration("Banking", "Banking Transaction Management System", Complexity.ADVANCED, 7,
                List.of("Java", "Spring Boot", "MySQL", "Hibernate/JPA", "Kafka", "Docker"),
                List.of("REST API", "Kafka"), List.of("Caching"), null);
    }

    public static BlueprintContent blueprint() {
        return new BlueprintContent("Banking Transaction Service", "Banks need an auditable ledger.",
                "Accounts and transfers.",
                List.of(new BlueprintContent.Module("accounts", "Account management")),
                List.of(
                        new BlueprintContent.Requirement("REQ-001", "Create account", "POST /api/accounts",
                                Category.FUNCTIONAL, true, List.of("returns 201")),
                        new BlueprintContent.Requirement("REQ-002", "Create transaction", "POST /api/transactions",
                                Category.FUNCTIONAL, true, List.of("idempotent on Idempotency-Key")),
                        new BlueprintContent.Requirement("REQ-003", "Publish transaction event", "Kafka event",
                                Category.MESSAGING, true, List.of("event on transactions.created")),
                        new BlueprintContent.Requirement("REQ-004", "Cache balances", "optional cache",
                                Category.NON_FUNCTIONAL, false, List.of("cache hit"))),
                List.of(new BlueprintContent.ApiEndpoint("POST", "/api/accounts", "Create", List.of("REQ-001"))),
                List.of(new BlueprintContent.DataEntity("Account", null, List.of("id", "balance"))),
                List.of(new BlueprintContent.MessagingEvent("TransactionCreated", "transactions.created", "api",
                        "audit", "{id, amount}", List.of("REQ-003"))),
                List.of("p95 < 200ms"), List.of("JWT auth"), List.of("unit and integration tests"),
                List.of("correctness"), "Build the service described below.");
    }

    public static CodeAnalysisResult codeAnalysis() {
        return new CodeAnalysisResult("Solid.",
                List.of(
                        new CodeAnalysisResult.RequirementCoverage("REQ-001", CoverageStatus.IMPLEMENTED,
                                List.of("AccountController.java#create"), List.of("AccountControllerTest"), null),
                        new CodeAnalysisResult.RequirementCoverage("REQ-002", CoverageStatus.PARTIALLY_IMPLEMENTED,
                                List.of("TransactionService.java#process"), List.of(), "no idempotency"),
                        new CodeAnalysisResult.RequirementCoverage("REQ-999", CoverageStatus.IMPLEMENTED,
                                List.of(), List.of(), "hallucinated")),
                List.of(new CodeAnalysisResult.AreaAssessment("Testing", 6, List.of(), List.of())),
                List.of(), List.of());
    }

    public static GeneratedMcqs mcqs(int count) {
        McqCategory[] categories = McqCategory.values();
        List<GeneratedMcqs.Question> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            questions.add(new GeneratedMcqs.Question(categories[i % categories.length], "Question " + i,
                    List.of("a" + i, "b" + i, "c" + i, "d" + i), 1, "because", "TransactionService.java#process",
                    "REQ-002"));
        }
        return new GeneratedMcqs(questions);
    }
}
