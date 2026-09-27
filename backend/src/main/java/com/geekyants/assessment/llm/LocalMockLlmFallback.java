package com.geekyants.assessment.llm;

import com.geekyants.assessment.analysis.RequirementAnalysis;
import com.geekyants.assessment.analysis.RequirementAnalysis.Complexity;
import com.geekyants.assessment.analysis.RequirementAnalysis.Level;
import com.geekyants.assessment.analysis.RequirementAnalysis.ProjectIdea;
import com.geekyants.assessment.analysis.RequirementAnalysis.SkillAssessment;
import com.geekyants.assessment.blueprint.BlueprintContent;
import com.geekyants.assessment.blueprint.BlueprintContent.ApiEndpoint;
import com.geekyants.assessment.blueprint.BlueprintContent.Category;
import com.geekyants.assessment.blueprint.BlueprintContent.DataEntity;
import com.geekyants.assessment.blueprint.BlueprintContent.MessagingEvent;
import com.geekyants.assessment.blueprint.BlueprintContent.Module;
import com.geekyants.assessment.blueprint.BlueprintContent.Requirement;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult.AreaAssessment;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult.CoverageStatus;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult.Finding;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult.ImplementationDecision;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult.RequirementCoverage;
import com.geekyants.assessment.codeanalysis.CodeAnalysisResult.Severity;
import com.geekyants.assessment.mcq.GeneratedMcqs;
import com.geekyants.assessment.mcq.McqCategory;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides deterministic, schema-compliant fallback outputs for local development and demos
 * when ANTHROPIC_API_KEY is not configured.
 */
public final class LocalMockLlmFallback {

    private LocalMockLlmFallback() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T generate(String task, Class<T> type) {
        if (type == RequirementAnalysis.class) {
            return (T) requirementAnalysis();
        }
        if (type == BlueprintContent.class) {
            return (T) blueprintContent();
        }
        if (type == CodeAnalysisResult.class) {
            return (T) codeAnalysisResult();
        }
        if (type == GeneratedMcqs.class) {
            return (T) generatedMcqs();
        }
        throw new UnsupportedOperationException("No local fallback for type: " + type.getName());
    }

    private static RequirementAnalysis requirementAnalysis() {
        return new RequirementAnalysis(
                "Senior backend candidate with extensive Java and Spring Boot experience. Proficient in relational database design with MySQL, but has a slight gap in production Apache Kafka streaming pipelines.",
                "Banking",
                List.of(
                        new SkillAssessment("Java", true, Level.STRONG, Level.STRONG, "6 years enterprise experience"),
                        new SkillAssessment("Spring Boot", true, Level.STRONG, Level.STRONG, "Production microservices"),
                        new SkillAssessment("MySQL", true, Level.INTERMEDIATE, Level.STRONG, "High-volume schema design"),
                        new SkillAssessment("Kafka", true, Level.INTERMEDIATE, Level.BEGINNER, "Academic and lab experiments only")
                ),
                List.of("Kafka"),
                List.of("Java", "Spring Boot", "MySQL", "Hibernate/JPA", "Kafka", "Docker"),
                Complexity.ADVANCED,
                List.of("Idempotent financial transactions", "Event-driven messaging via Kafka", "Audit logging"),
                List.of(
                        new ProjectIdea("Banking Transaction Management System", "Tests transaction idempotency, consistency, and asynchronous audit events with Kafka."),
                        new ProjectIdea("Real-time Fraud Alert Pipeline", "Tests event streaming and balance threshold evaluations.")
                )
        );
    }

    private static BlueprintContent blueprintContent() {
        return new BlueprintContent(
                "Banking Transaction Management System",
                "Financial institutions require robust, idempotent transaction processing with guaranteed audit logging and event propagation.",
                "Implement customer account creation, transfer processing with idempotency keys, and Kafka transaction event publishing.",
                List.of(
                        new Module("accounts", "Account onboarding and balance management"),
                        new Module("transactions", "Idempotent financial transfer processing"),
                        new Module("events", "Kafka event publication and audit streaming")
                ),
                List.of(
                        new Requirement("REQ-001", "Create customer account", "API endpoint to register account with currency and initial balance", Category.FUNCTIONAL, true, List.of("Returns 201 Created with valid account details", "Rejects negative opening balances")),
                        new Requirement("REQ-002", "Idempotent transaction transfer", "Transfer funds between accounts using Idempotency-Key header", Category.FUNCTIONAL, true, List.of("Deducts source account and credits destination account atomically", "Repeated calls with same Idempotency-Key return cached response")),
                        new Requirement("REQ-003", "Publish transaction event to Kafka", "Publish TransactionCreated event upon successful transfer", Category.MESSAGING, true, List.of("Emits event to 'transactions.created' topic", "Payload includes transactionId, source, destination, amount and timestamp")),
                        new Requirement("REQ-004", "Maintain audit log and balance cache", "Store immutable transaction audit trail and cache hot account balances", Category.NON_FUNCTIONAL, false, List.of("P95 latency under 200ms", "Audit table records all balance mutations"))
                ),
                List.of(
                        new ApiEndpoint("POST", "/api/accounts", "Register a new customer account", List.of("REQ-001")),
                        new ApiEndpoint("POST", "/api/transactions", "Execute a transfer between accounts", List.of("REQ-002", "REQ-003")),
                        new ApiEndpoint("GET", "/api/accounts/{id}", "Fetch account balance and status", List.of("REQ-001", "REQ-004"))
                ),
                List.of(
                        new DataEntity("Account", "Customer account with balance", List.of("id", "accountNumber", "balance", "currency", "createdAt")),
                        new DataEntity("Transaction", "Transfer record", List.of("id", "idempotencyKey", "sourceAccountId", "targetAccountId", "amount", "status", "timestamp"))
                ),
                List.of(
                        new MessagingEvent("TransactionCreated", "transactions.created", "TransactionService", "AuditConsumer", "{\"transactionId\":\"UUID\",\"amount\":0.0}", List.of("REQ-003"))
                ),
                List.of("P95 response time < 200ms", "Zero lost updates on concurrent transfers"),
                List.of("Strict role-based authorization", "Prevent balance tampering"),
                List.of("Unit tests for transfer logic", "Integration tests with Testcontainers MySQL & Kafka"),
                List.of("Correctness & Concurrency handling", "Code structure and idiomatic Spring Boot", "Kafka event reliability"),
                "Please clone the repository, implement all requirements in REQ-001 through REQ-004, ensure `./mvnw verify` passes, and commit your work."
        );
    }

    private static CodeAnalysisResult codeAnalysisResult() {
        return new CodeAnalysisResult(
                "The candidate implemented core account and transaction management with solid Spring Boot conventions. Concurrency control on transfer is handled via pessimistic locking. Kafka event publishing is functional, with minor opportunity for transactional outbox pattern.",
                List.of(
                        new RequirementCoverage("REQ-001", CoverageStatus.IMPLEMENTED, List.of("src/main/java/com/geekyants/assessment/controller/AccountController.java#createAccount"), List.of("AccountControllerTest#shouldCreateAccount"), "Account creation validated with input validation."),
                        new RequirementCoverage("REQ-002", CoverageStatus.IMPLEMENTED, List.of("src/main/java/com/geekyants/assessment/service/TransactionService.java#processTransaction"), List.of("TransactionServiceTest#testIdempotency"), "Idempotency key verified using database index."),
                        new RequirementCoverage("REQ-003", CoverageStatus.IMPLEMENTED, List.of("src/main/java/com/geekyants/assessment/event/TransactionEventProducer.java#publishEvent"), List.of("KafkaIntegrationTest#testEventPublished"), "Emits to transactions.created topic on success."),
                        new RequirementCoverage("REQ-004", CoverageStatus.PARTIALLY_IMPLEMENTED, List.of("src/main/java/com/geekyants/assessment/service/AuditService.java#recordAudit"), List.of(), "Audit trail implemented; Redis caching omitted.")
                ),
                List.of(
                        new AreaAssessment("Architecture", 8, List.of("Clean package by feature", "Proper separation of DTOs and entities"), List.of()),
                        new AreaAssessment("REST APIs", 9, List.of("Appropriate HTTP status codes", "Standardized ProblemDetail error responses"), List.of()),
                        new AreaAssessment("Messaging", 7, List.of("Clean KafkaTemplate usage"), List.of("No dead letter queue configured")),
                        new AreaAssessment("Testing", 8, List.of("Comprehensive unit and slice tests"), List.of("Kafka test uses Mockito rather than Testcontainers"))
                ),
                List.of(
                        new Finding(Severity.MEDIUM, "Messaging", "TransactionEventProducer.java", "publishEvent", "Direct Kafka send inside database transaction without transactional outbox.", "Consider transactional outbox to prevent phantom messages on DB rollback.")
                ),
                List.of(
                        new ImplementationDecision("TransactionService.java", "processTransaction", "Used database unique constraint on idempotency_key rather than distributed lock.", "@Transactional\npublic Transaction processTransaction(TransactionRequest req) { ... }", "REQ-002")
                )
        );
    }

    private static GeneratedMcqs generatedMcqs() {
        List<GeneratedMcqs.Question> list = new ArrayList<>();
        list.add(new GeneratedMcqs.Question(
                McqCategory.PROJECT_UNDERSTANDING,
                "In the Banking Transaction Management System, what is the primary purpose of the 'Idempotency-Key' header?",
                List.of("To authenticate the client application", "To prevent duplicate transaction execution on network retry", "To encrypt sensitive transfer details", "To route the request to a specific microservice instance"),
                1,
                "Idempotency keys ensure that retried network requests execute the transfer operation at most once.",
                "src/main/java/com/geekyants/assessment/service/TransactionService.java#processTransaction",
                "REQ-002"
        ));
        list.add(new GeneratedMcqs.Question(
                McqCategory.CODE_UNDERSTANDING,
                "In your TransactionService.java, how is concurrent transfer race conditions between overlapping account updates mitigated?",
                List.of("Optimistic locking with @Version annotation", "Pessimistic write locking on the Account entity during balance check", "Single-threaded synchronization at JVM level", "Relying on database read-uncommitted isolation"),
                1,
                "The implementation uses select ... for update (pessimistic lock) to guarantee serialized balance updates.",
                "src/main/java/com/geekyants/assessment/service/TransactionService.java#processTransaction",
                "REQ-002"
        ));
        list.add(new GeneratedMcqs.Question(
                McqCategory.MESSAGING,
                "In TransactionEventProducer.java, what happens to the Kafka event if the database transaction fails after the message is sent?",
                List.of("Kafka automatically rolls back the published message", "The message remains in the topic because Kafka is not enrolled in the JTA transaction", "The application throws an UnsupportedOperationException", "The message is moved to the dead-letter topic"),
                1,
                "Without the Transactional Outbox pattern or two-phase commit, non-transactional Kafka sends can produce phantom events if the database rolls back.",
                "src/main/java/com/geekyants/assessment/event/TransactionEventProducer.java#publishEvent",
                "REQ-003"
        ));
        list.add(new GeneratedMcqs.Question(
                McqCategory.DATABASE,
                "Why was the 'idempotency_key' column indexed with a UNIQUE constraint in the transactions table?",
                List.of("To improve full-text search performance", "To ensure atomic duplicate detection at the database storage engine level", "To automatically cluster records chronologically", "To fulfill foreign key constraints with the accounts table"),
                1,
                "A database unique constraint guarantees that concurrent requests cannot insert conflicting records for the same key.",
                "src/main/java/com/geekyants/assessment/service/TransactionService.java#processTransaction",
                "REQ-002"
        ));
        list.add(new GeneratedMcqs.Question(
                McqCategory.ARCHITECTURE,
                "What architectural pattern would decouple the local database transaction from Kafka event publication in REQ-003?",
                List.of("CQRS with Event Sourcing", "Transactional Outbox Pattern with Change Data Capture (Debezium)", "Saga Pattern with Orchestrator", "Bulkhead Pattern"),
                1,
                "The Transactional Outbox pattern writes the event into an outbox table in the same DB transaction, eliminating dual-write inconsistency.",
                "src/main/java/com/geekyants/assessment/event/TransactionEventProducer.java#publishEvent",
                "REQ-003"
        ));
        list.add(new GeneratedMcqs.Question(
                McqCategory.CODE_UNDERSTANDING,
                "In AccountController.java, how did you handle client validation when an opening balance is negative?",
                List.of("Through custom @PositiveOrZero Bean Validation on the request record", "By throwing a RuntimeException in the controller method", "By catching SQLIntegrityConstraintViolationException", "By returning HTTP 200 with an error flag"),
                0,
                "Input records are decorated with @PositiveOrZero and validated with Spring's @Valid.",
                "src/main/java/com/geekyants/assessment/controller/AccountController.java#createAccount",
                "REQ-001"
        ));
        list.add(new GeneratedMcqs.Question(
                McqCategory.TECHNICAL_CONCEPTS,
                "In Spring Boot 4.x / 3.x, what is the recommended way to return RFC 7807 structured HTTP error details?",
                List.of("ResponseEntity<Map<String, Object>>", "ProblemDetail (or ProblemDetail-based exceptions)", "Custom ErrorDTO with 200 OK status", "HttpServletResponse.sendError()"),
                1,
                "Spring 6+ integrates ProblemDetail for RFC 7807 compliance across REST APIs.",
                "",
                ""
        ));
        list.add(new GeneratedMcqs.Question(
                McqCategory.SECURITY,
                "How does the platform ensure that candidate assessment answer submissions cannot be spoofed or accessed by unauthorized candidates?",
                List.of("By validating candidate_id claim in the JWT against the assignment owner", "By storing candidate passwords in plain text", "By allowing public CORS headers everywhere", "By using client-side cookie verification only"),
                0,
                "The resource server enforces role=CANDIDATE and candidate_id claim matching the assignment owner.",
                "",
                ""
        ));
        list.add(new GeneratedMcqs.Question(
                McqCategory.SCENARIO,
                "If two transfers for account A occur simultaneously, each attempting to withdraw $100 when the balance is $150, what prevents an overdraft?",
                List.of("Thread.sleep backoff", "Pessimistic lock on account record serializing the read and balance check", "Client-side retry logic", "Asynchronous thread pool isolation"),
                1,
                "Pessimistic locking ensures the second transaction waits until the first commits, reading the updated $50 balance and rejecting the transfer.",
                "src/main/java/com/geekyants/assessment/service/TransactionService.java#processTransaction",
                "REQ-002"
        ));
        list.add(new GeneratedMcqs.Question(
                McqCategory.CODE_UNDERSTANDING,
                "In your AuditService.java, why are balance mutation audit logs kept append-only?",
                List.of("To reduce disk space consumption", "For regulatory auditability, non-repudiation, and forensic traceability", "Because MySQL does not allow UPDATE statements on tables with timestamps", "To avoid generating undo logs"),
                1,
                "Financial compliance requires immutable, append-only logs for all balance-modifying actions.",
                "src/main/java/com/geekyants/assessment/service/AuditService.java#recordAudit",
                "REQ-004"
        ));
        list.add(new GeneratedMcqs.Question(
                McqCategory.TECHNICAL_CONCEPTS,
                "What is the default isolation level in MySQL InnoDB, and how does it behave during read queries?",
                List.of("READ UNCOMMITTED with dirty reads", "REPEATABLE READ with consistent non-locking reads via MVCC", "SERIALIZABLE with table locks", "READ COMMITTED without snapshot isolation"),
                1,
                "InnoDB defaults to REPEATABLE READ, using MVCC snapshots to avoid phantom reads for ordinary selects.",
                "",
                ""
        ));
        list.add(new GeneratedMcqs.Question(
                McqCategory.MESSAGING,
                "When publishing events to a Kafka topic partitioned across multiple brokers, what should be used as the message key to maintain order per account?",
                List.of("A random UUID", "The accountId or customerId", "The current system timestamp", "Leaving the key null"),
                1,
                "Kafka guarantees in-order delivery within a single partition; hashing by accountId ensures all events for that account land on the same partition.",
                "src/main/java/com/geekyants/assessment/event/TransactionEventProducer.java#publishEvent",
                "REQ-003"
        ));
        list.add(new GeneratedMcqs.Question(
                McqCategory.PROJECT_UNDERSTANDING,
                "Why is the candidate repository frozen with read-only permissions immediately when Submit is clicked?",
                List.of("To save GitHub storage quota", "To ensure the exact commit SHA being evaluated cannot be altered or force-pushed", "To delete the candidate branch", "To notify the hiring manager via email"),
                1,
                "Submission freeze guarantees immutability so evaluation and code-anchored MCQs analyze the exact submitted state.",
                "",
                ""
        ));
        return new GeneratedMcqs(list);
    }
}
