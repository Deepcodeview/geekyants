package com.geekyants.assessment.candidate;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.blueprint.BlueprintContent;
import com.geekyants.assessment.blueprint.BlueprintService;
import com.geekyants.assessment.blueprint.ProjectConfiguration;
import com.geekyants.assessment.candidate.CandidateViews.AssessmentAttempt;
import com.geekyants.assessment.candidate.CandidateViews.AssessmentInfo;
import com.geekyants.assessment.candidate.CandidateViews.AssignmentItem;
import com.geekyants.assessment.candidate.CandidateViews.CiRunView;
import com.geekyants.assessment.candidate.CandidateViews.Dashboard;
import com.geekyants.assessment.candidate.CandidateViews.PushView;
import com.geekyants.assessment.candidate.CandidateViews.QuestionView;
import com.geekyants.assessment.candidate.CandidateViews.RequirementView;
import com.geekyants.assessment.candidate.CandidateViews.SubmissionView;
import com.geekyants.assessment.common.Json;
import com.geekyants.assessment.config.CurrentUser;
import com.geekyants.assessment.github.GithubRepo;
import com.geekyants.assessment.github.GithubRepoRepository;
import com.geekyants.assessment.mcq.McqAssessment;
import com.geekyants.assessment.mcq.McqAssessmentRepository;
import com.geekyants.assessment.mcq.McqQuestion;
import com.geekyants.assessment.mcq.McqService;
import com.geekyants.assessment.submission.Submission;
import com.geekyants.assessment.submission.SubmissionRepository;
import com.geekyants.assessment.submission.SubmissionService;
import com.geekyants.assessment.webhook.CiRunRepository;
import com.geekyants.assessment.webhook.PushEventRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/api/v1/candidate")
class CandidateController {

    /** Before these states the project is still being designed and is not shown to the candidate. */
    private static final Set<AssignmentStatus> PRE_ASSIGNMENT = EnumSet.of(
            AssignmentStatus.CREATED, AssignmentStatus.ANALYZING_REQUIREMENTS, AssignmentStatus.ANALYZED,
            AssignmentStatus.BLUEPRINT_GENERATING, AssignmentStatus.BLUEPRINT_DRAFT,
            AssignmentStatus.BLUEPRINT_APPROVED, AssignmentStatus.PROVISIONING);

    private final CandidateService candidates;
    private final SubmissionService submissionService;
    private final McqService mcqs;
    private final BlueprintService blueprints;
    private final GithubRepoRepository repos;
    private final CiRunRepository ciRuns;
    private final PushEventRepository pushes;
    private final SubmissionRepository submissions;
    private final McqAssessmentRepository assessments;
    private final CurrentUser currentUser;
    private final Json json;

    CandidateController(CandidateService candidates, SubmissionService submissionService, McqService mcqs,
                        BlueprintService blueprints, GithubRepoRepository repos, CiRunRepository ciRuns,
                        PushEventRepository pushes, SubmissionRepository submissions,
                        McqAssessmentRepository assessments, CurrentUser currentUser, Json json) {
        this.candidates = candidates;
        this.submissionService = submissionService;
        this.mcqs = mcqs;
        this.blueprints = blueprints;
        this.repos = repos;
        this.ciRuns = ciRuns;
        this.pushes = pushes;
        this.submissions = submissions;
        this.assessments = assessments;
        this.currentUser = currentUser;
        this.json = json;
    }

    @GetMapping("/assignments")
    @Transactional(readOnly = true)
    List<AssignmentItem> list() {
        return candidates.assignments(currentUser.candidateId()).stream()
                .map(a -> new AssignmentItem(a.getPublicId(), a.getJob().getTitle(), a.getCompany().getName(),
                        visibleStatus(a), a.getDueAt()))
                .toList();
    }

    @GetMapping("/assignments/{id}")
    @Transactional(readOnly = true)
    Dashboard dashboard(@PathVariable String id) {
        Assignment a = candidates.own(currentUser.candidateId(), id);
        if (PRE_ASSIGNMENT.contains(a.getStatus()) || a.getStatus() == AssignmentStatus.FAILED
                && a.getAssignedAt() == null) {
            return new Dashboard(a.getPublicId(), visibleStatus(a), a.getJob().getTitle(), a.getCompany().getName(),
                    null, null, List.of(), List.of(), null, null);
        }
        ProjectConfiguration config = json.read(a.getProjectConfigJson(), ProjectConfiguration.class);
        BlueprintContent bp = blueprints.content(blueprints.approved(a));
        GithubRepo repo = repos.findByAssignment(a).orElseThrow();

        List<RequirementView> requirements = bp.requirements().stream()
                .map(r -> new RequirementView(r.code(), r.title(), r.description(), r.mandatory(),
                        r.acceptanceCriteria()))
                .toList();
        CandidateViews.Project project = new CandidateViews.Project(bp.projectName(), config.domain(),
                config.technologies(), config.durationDays(), a.getAssignedAt(), a.getDueAt(), bp.businessProblem(),
                bp.candidateInstructions(), requirements);
        CandidateViews.Repository repository = new CandidateViews.Repository(repo.getFullName(), repo.getHtmlUrl(),
                repo.getHtmlUrl() == null ? null : repo.getHtmlUrl() + ".git", repo.getCandidateAccess().name(),
                a.getCandidate().getGithubUsername());

        List<CiRunView> runs = ciRuns.findTop10ByRepositoryOrderByUpdatedAtDesc(repo).stream()
                .map(r -> new CiRunView(r.getHeadSha(), r.getWorkflowName(), r.getStatus(), r.getConclusion(),
                        r.getHtmlUrl(), r.getUpdatedAt()))
                .toList();
        List<PushView> recent = pushes.findTop20ByRepositoryOrderByReceivedAtDesc(repo).stream().limit(5)
                .map(p -> new PushView(p.getAfterSha(), p.getHeadCommitMessage(), p.getCommitCount(), p.getReceivedAt()))
                .toList();
        SubmissionView submission = submissions.findFirstByAssignmentOrderByAttemptNoDesc(a)
                .map(s -> new SubmissionView(s.getPublicId(), s.getCommitSha(), s.getSubmittedAt(), s.getTrigger().name()))
                .orElse(null);
        AssessmentInfo assessment = assessments.findFirstByAssignmentOrderByCreatedAtDesc(a)
                .map(m -> new AssessmentInfo(m.getStatus().name(), m.getQuestions().size(), m.getTimeLimitMinutes(),
                        m.getDeadlineAt()))
                .orElse(null);
        return new Dashboard(a.getPublicId(), visibleStatus(a), a.getJob().getTitle(), a.getCompany().getName(),
                project, repository, runs, recent, submission, assessment);
    }

    record GithubUsernameRequest(@NotBlank String username) {
    }

    @PutMapping("/github-username")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void linkGithub(@RequestBody GithubUsernameRequest request) {
        candidates.linkGithubUsername(currentUser.candidateId(), request.username());
    }

    @PostMapping("/assignments/{id}/submit")
    SubmissionView submit(@PathVariable String id) {
        String candidateId = currentUser.candidateId();
        candidates.own(candidateId, id);
        Submission s = submissionService.submit(id, Submission.Trigger.CANDIDATE, candidateId);
        return new SubmissionView(s.getPublicId(), s.getCommitSha(), s.getSubmittedAt(), s.getTrigger().name());
    }

    @PostMapping("/assignments/{id}/assessment/start")
    AssessmentAttempt startAssessment(@PathVariable String id) {
        Assignment a = candidates.own(currentUser.candidateId(), id);
        mcqs.start(a, currentUser.candidateId());
        return attempt(id);
    }

    @GetMapping("/assignments/{id}/assessment")
    @Transactional(readOnly = true)
    AssessmentAttempt attempt(@PathVariable String id) {
        Assignment a = candidates.own(currentUser.candidateId(), id);
        McqAssessment m = mcqs.current(a);
        // Questions are revealed only while the timed attempt is running.
        List<QuestionView> questions = m.getStatus() != McqAssessment.Status.IN_PROGRESS ? List.of()
                : m.getQuestions().stream().map(this::question).toList();
        return new AssessmentAttempt(m.getPublicId(), m.getStatus().name(), m.getDeadlineAt(), questions);
    }

    record AnswerRequest(@NotNull Integer selectedOption) {
    }

    @PutMapping("/assignments/{id}/assessment/answers/{questionCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void answer(@PathVariable String id, @PathVariable String questionCode, @RequestBody AnswerRequest request) {
        Assignment a = candidates.own(currentUser.candidateId(), id);
        mcqs.answer(a, questionCode, request.selectedOption());
    }

    @PostMapping("/assignments/{id}/assessment/finish")
    AssessmentAttempt finish(@PathVariable String id) {
        Assignment a = candidates.own(currentUser.candidateId(), id);
        mcqs.finish(a, currentUser.candidateId());
        return attempt(id);
    }

    private QuestionView question(McqQuestion q) {
        List<String> options = json.read(q.getOptionsJson(), new TypeReference<List<String>>() {
        });
        return new QuestionView(q.getCode(), q.getPosition(), q.getCategory().name(), q.getStem(), options,
                q.getAnswer() == null ? null : q.getAnswer().getSelectedOption());
    }

    /** Collapses internal states so the candidate sees their stage, not pipeline mechanics. */
    private static String visibleStatus(Assignment a) {
        return switch (a.getStatus()) {
            case CREATED, ANALYZING_REQUIREMENTS, ANALYZED, BLUEPRINT_GENERATING, BLUEPRINT_DRAFT,
                 BLUEPRINT_APPROVED, PROVISIONING -> "PREPARING";
            case AWAITING_GITHUB_USERNAME -> "AWAITING_GITHUB_USERNAME";
            case ASSIGNED, IN_PROGRESS -> "IN_DEVELOPMENT";
            case SUBMITTED, EVALUATING, ANALYZING_CODE, GENERATING_MCQ -> "EVALUATING";
            case MCQ_READY -> "ASSESSMENT_READY";
            case MCQ_IN_PROGRESS -> "ASSESSMENT_IN_PROGRESS";
            case MCQ_COMPLETED, REPORTED -> "COMPLETED";
            case FAILED -> "ON_HOLD";
        };
    }
}
