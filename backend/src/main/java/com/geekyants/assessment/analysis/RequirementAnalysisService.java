package com.geekyants.assessment.analysis;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentRepository;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.assignment.PipelineEvents.RequirementAnalysisRequested;
import com.geekyants.assessment.assignment.StepRunner;
import com.geekyants.assessment.audit.AuditLog;
import com.geekyants.assessment.common.ConflictException;
import com.geekyants.assessment.common.Json;
import com.geekyants.assessment.common.NotFoundException;
import com.geekyants.assessment.llm.LlmClient;
import com.geekyants.assessment.llm.LlmClient.LlmRequest;
import com.geekyants.assessment.llm.PromptTemplates;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

@Service
public class RequirementAnalysisService {

    private final AssignmentRepository assignments;
    private final LlmClient llm;
    private final PromptTemplates prompts;
    private final Json json;
    private final AuditLog audit;
    private final StepRunner steps;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;

    public RequirementAnalysisService(AssignmentRepository assignments, LlmClient llm, PromptTemplates prompts,
                                      Json json, AuditLog audit, StepRunner steps, TransactionTemplate tx,
                                      ApplicationEventPublisher events) {
        this.assignments = assignments;
        this.llm = llm;
        this.prompts = prompts;
        this.json = json;
        this.audit = audit;
        this.steps = steps;
        this.tx = tx;
        this.events = events;
    }

    /** Admin re-run, e.g. after the recruitment system sent an updated JD. */
    @Transactional
    public void rerun(String assignmentId, String actor) {
        Assignment assignment = assignments.findByPublicId(assignmentId)
                .orElseThrow(() -> new NotFoundException("Assignment", assignmentId));
        if (assignment.getStatus() != AssignmentStatus.ANALYZED) {
            throw new ConflictException("Requirement analysis can only be re-run before the blueprint is generated");
        }
        assignment.transitionTo(AssignmentStatus.ANALYZING_REQUIREMENTS);
        audit.record(assignment, "ANALYSIS_RERUN", actor, null);
        events.publishEvent(new RequirementAnalysisRequested(assignmentId));
    }

    @Async
    @TransactionalEventListener
    public void onRequested(RequirementAnalysisRequested event) {
        steps.run(event.assignmentId(), "requirement-analysis", () -> analyze(event.assignmentId()));
    }

    void analyze(String assignmentId) {
        String input = tx.execute(status -> {
            Assignment a = assignments.findByPublicId(assignmentId).orElseThrow();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("company", json.read(a.getCompany().getStandardsJson(), JsonNode.class));
            data.put("job", json.read(a.getJob().getDescriptionJson(), JsonNode.class));
            data.put("candidate", json.read(a.getCandidate().getProfileJson(), JsonNode.class));
            return json.write(data);
        });

        RequirementAnalysis result = llm.generate(new LlmRequest("requirement-analysis",
                prompts.system("requirement-analysis"),
                "<input>\n" + input + "\n</input>"), RequirementAnalysis.class);

        tx.executeWithoutResult(status -> {
            Assignment a = assignments.findByPublicId(assignmentId).orElseThrow();
            a.setRequirementAnalysisJson(json.write(result));
            a.transitionTo(AssignmentStatus.ANALYZED);
            audit.record(a, "ANALYSIS_COMPLETED", AuditLog.SYSTEM,
                    "gaps=" + (result.skillGaps() == null ? 0 : result.skillGaps().size()));
        });
    }
}
