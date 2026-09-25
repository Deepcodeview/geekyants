package com.geekyants.assessment.blueprint;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentRepository;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.assignment.PipelineEvents.BlueprintRequested;
import com.geekyants.assessment.assignment.PipelineEvents.ProvisioningRequested;
import com.geekyants.assessment.assignment.StepRunner;
import com.geekyants.assessment.audit.AuditLog;
import com.geekyants.assessment.common.ConflictException;
import com.geekyants.assessment.common.Ids;
import com.geekyants.assessment.common.Json;
import com.geekyants.assessment.common.NotFoundException;
import com.geekyants.assessment.generation.TemplateCatalog;
import com.geekyants.assessment.llm.LlmClient;
import com.geekyants.assessment.llm.LlmClient.LlmRequest;
import com.geekyants.assessment.llm.LlmException;
import com.geekyants.assessment.llm.PromptTemplates;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@Service
public class BlueprintService {

    private static final Set<AssignmentStatus> CONFIGURABLE =
            EnumSet.of(AssignmentStatus.ANALYZED, AssignmentStatus.BLUEPRINT_DRAFT);

    private final AssignmentRepository assignments;
    private final BlueprintRepository blueprints;
    private final LlmClient llm;
    private final PromptTemplates prompts;
    private final Json json;
    private final Validator validator;
    private final AuditLog audit;
    private final StepRunner steps;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;

    public BlueprintService(AssignmentRepository assignments, BlueprintRepository blueprints, LlmClient llm,
                            PromptTemplates prompts, Json json, Validator validator, AuditLog audit,
                            StepRunner steps, TransactionTemplate tx, ApplicationEventPublisher events) {
        this.assignments = assignments;
        this.blueprints = blueprints;
        this.llm = llm;
        this.prompts = prompts;
        this.json = json;
        this.validator = validator;
        this.audit = audit;
        this.steps = steps;
        this.tx = tx;
        this.events = events;
    }

    @Transactional
    public Assignment configure(String assignmentId, ProjectConfiguration config, String actor) {
        Assignment assignment = load(assignmentId);
        if (!CONFIGURABLE.contains(assignment.getStatus())) {
            throw new ConflictException("Project configuration is locked once the blueprint is approved");
        }
        if (TemplateCatalog.select(config.technologies()).isEmpty()) {
            throw new IllegalArgumentException("No project template supports " + config.technologies()
                    + ". Supported: Java + Spring Boot.");
        }
        assignment.configure(json.write(config), config.durationDays());
        audit.record(assignment, "PROJECT_CONFIGURED", actor, json.write(config));
        return assignment;
    }

    /** Generates the first blueprint, or regenerates a draft with optional admin instructions. */
    @Transactional
    public Assignment requestGeneration(String assignmentId, String adminInstructions, String actor) {
        Assignment assignment = load(assignmentId);
        if (!CONFIGURABLE.contains(assignment.getStatus())) {
            throw new ConflictException("Blueprints can only be generated before approval");
        }
        if (assignment.getProjectConfigJson() == null) {
            throw new ConflictException("Configure the project before generating a blueprint");
        }
        assignment.transitionTo(AssignmentStatus.BLUEPRINT_GENERATING);
        audit.record(assignment, "BLUEPRINT_REQUESTED", actor, adminInstructions);
        events.publishEvent(new BlueprintRequested(assignmentId, adminInstructions));
        return assignment;
    }

    @Async
    @TransactionalEventListener
    public void onRequested(BlueprintRequested event) {
        steps.run(event.assignmentId(), "blueprint-generation",
                () -> generate(event.assignmentId(), event.adminInstructions()));
    }

    void generate(String assignmentId, String adminInstructions) {
        String input = tx.execute(status -> {
            Assignment a = load(assignmentId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("company", json.read(a.getCompany().getStandardsJson(), JsonNode.class));
            data.put("job", json.read(a.getJob().getDescriptionJson(), JsonNode.class));
            data.put("requirementAnalysis", json.read(a.getRequirementAnalysisJson(), JsonNode.class));
            data.put("projectConfiguration", json.read(a.getProjectConfigJson(), JsonNode.class));
            blueprints.findFirstByAssignmentOrderByVersionNoDesc(a)
                    .ifPresent(b -> data.put("previousDraft", json.read(b.getContentJson(), JsonNode.class)));
            if (adminInstructions != null && !adminInstructions.isBlank()) {
                data.put("adminInstructions", adminInstructions);
            }
            return json.write(data);
        });

        BlueprintContent content = llm.generate(new LlmRequest("blueprint",
                prompts.system("blueprint"), "<input>\n" + input + "\n</input>"), BlueprintContent.class);
        List<String> errors = validationErrors(content);
        if (!errors.isEmpty()) {
            throw new LlmException("Generated blueprint is invalid: " + String.join("; ", errors));
        }

        tx.executeWithoutResult(status -> {
            Assignment a = load(assignmentId);
            Blueprint saved = saveNewVersion(a, content);
            a.transitionTo(AssignmentStatus.BLUEPRINT_DRAFT);
            audit.record(a, "BLUEPRINT_GENERATED", AuditLog.SYSTEM, saved.getPublicId());
        });
    }

    /** Admin edit. Stored as a new version so the history of what changed stays traceable. */
    @Transactional
    public Blueprint edit(String assignmentId, BlueprintContent content, String actor) {
        Assignment assignment = load(assignmentId);
        if (assignment.getStatus() != AssignmentStatus.BLUEPRINT_DRAFT) {
            throw new ConflictException("Only a draft blueprint can be edited");
        }
        List<String> errors = validationErrors(content);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
        Blueprint saved = saveNewVersion(assignment, content);
        audit.record(assignment, "BLUEPRINT_EDITED", actor, saved.getPublicId());
        return saved;
    }

    @Transactional
    public Blueprint approve(String assignmentId, String blueprintId, String actor) {
        Assignment assignment = load(assignmentId);
        if (assignment.getStatus() != AssignmentStatus.BLUEPRINT_DRAFT) {
            throw new ConflictException("Assignment has no draft blueprint awaiting approval");
        }
        Blueprint draft = blueprints.findByAssignmentAndStatus(assignment, BlueprintStatus.DRAFT)
                .orElseThrow(() -> new NotFoundException("Draft blueprint for", assignmentId));
        // Guards against approving a version the admin did not review (another admin edited meanwhile).
        if (!draft.getPublicId().equals(blueprintId)) {
            throw new ConflictException("Blueprint %s is not the current draft (%s)"
                    .formatted(blueprintId, draft.getPublicId()));
        }
        draft.approve(actor, Instant.now());
        assignment.transitionTo(AssignmentStatus.BLUEPRINT_APPROVED);
        audit.record(assignment, "BLUEPRINT_APPROVED", actor, blueprintId);

        assignment.transitionTo(AssignmentStatus.PROVISIONING);
        events.publishEvent(new ProvisioningRequested(assignmentId));
        return draft;
    }

    @Transactional(readOnly = true)
    public List<Blueprint> history(String assignmentId) {
        return blueprints.findByAssignmentOrderByVersionNoDesc(load(assignmentId));
    }

    @Transactional(readOnly = true)
    public Blueprint approved(Assignment assignment) {
        return blueprints.findByAssignmentAndStatus(assignment, BlueprintStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Approved blueprint for", assignment.getPublicId()));
    }

    public BlueprintContent content(Blueprint blueprint) {
        return json.read(blueprint.getContentJson(), BlueprintContent.class);
    }

    private Blueprint saveNewVersion(Assignment assignment, BlueprintContent content) {
        List<Blueprint> existing = blueprints.findByAssignmentOrderByVersionNoDesc(assignment);
        existing.stream().filter(b -> b.getStatus() == BlueprintStatus.DRAFT).forEach(Blueprint::supersede);
        int version = existing.isEmpty() ? 1 : existing.getFirst().getVersionNo() + 1;
        List<String> criteria = content.requirements().stream().map(r -> json.write(r.acceptanceCriteria())).toList();
        return blueprints.save(new Blueprint(Ids.next(Ids.BLUEPRINT), assignment, version, json.write(content),
                content.requirements(), criteria));
    }

    private List<String> validationErrors(BlueprintContent content) {
        List<String> errors = new ArrayList<>();
        for (ConstraintViolation<BlueprintContent> v : validator.validate(content)) {
            errors.add(v.getPropertyPath() + " " + v.getMessage());
        }
        if (errors.isEmpty()) {
            errors.addAll(BlueprintValidator.validate(content));
        }
        return errors;
    }

    private Assignment load(String assignmentId) {
        return assignments.findByPublicId(assignmentId)
                .orElseThrow(() -> new NotFoundException("Assignment", assignmentId));
    }
}
