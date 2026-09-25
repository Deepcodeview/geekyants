package com.geekyants.assessment.submission;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentRepository;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.assignment.PipelineEvents.SubmissionFrozen;
import com.geekyants.assessment.audit.AuditLog;
import com.geekyants.assessment.common.ConflictException;
import com.geekyants.assessment.common.Ids;
import com.geekyants.assessment.common.NotFoundException;
import com.geekyants.assessment.github.GitHubClient;
import com.geekyants.assessment.github.GithubRepo;
import com.geekyants.assessment.github.GithubRepoRepository;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Freezes the submission. Order matters: write access is revoked before the head is read, so no
 * push can land between "read head" and "tag head". The tag is protected by a ruleset, so the commit
 * stays reachable even if history is later rewritten.
 */
@Service
public class SubmissionService {

    private final AssignmentRepository assignments;
    private final GithubRepoRepository repos;
    private final SubmissionRepository submissions;
    private final GitHubClient github;
    private final AuditLog audit;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;

    public SubmissionService(AssignmentRepository assignments, GithubRepoRepository repos,
                             SubmissionRepository submissions, GitHubClient github, AuditLog audit,
                             TransactionTemplate tx, ApplicationEventPublisher events) {
        this.assignments = assignments;
        this.repos = repos;
        this.submissions = submissions;
        this.github = github;
        this.audit = audit;
        this.tx = tx;
        this.events = events;
    }

    record Target(String repoName, String branch, String username) {
    }

    /** Not transactional on purpose: GitHub calls happen between two short transactions. */
    public Submission submit(String assignmentId, Submission.Trigger trigger, String actor) {
        Target target = tx.execute(s -> {
            Assignment a = load(assignmentId);
            if (!a.getStatus().isDevelopmentOpen()) {
                throw new ConflictException("Assignment %s cannot be submitted in status %s"
                        .formatted(assignmentId, a.getStatus()));
            }
            if (trigger == Submission.Trigger.CANDIDATE && a.isPastDue(Instant.now())) {
                throw new ConflictException("The deadline has passed; your work is being submitted automatically");
            }
            GithubRepo repo = repos.findByAssignment(a).orElseThrow();
            return new Target(repo.getName(), repo.getDefaultBranch(), a.getCandidate().getGithubUsername());
        });

        github.setCollaborator(target.repoName(), target.username(), "pull");
        String headSha = github.branchHead(target.repoName(), target.branch());
        String submissionId = Ids.next(Ids.SUBMISSION);
        String tagRef = github.createTag(target.repoName(), "submission-" + submissionId, headSha);

        return tx.execute(s -> {
            Assignment a = load(assignmentId);
            // Re-check: a concurrent submit (candidate click racing the deadline job) may have won.
            if (!a.getStatus().isDevelopmentOpen()) {
                throw new ConflictException("Assignment %s was already submitted".formatted(assignmentId));
            }
            GithubRepo repo = repos.findByAssignment(a).orElseThrow();
            repo.setCandidateAccess(GithubRepo.Access.READ);
            int attempt = submissions.countByAssignment(a) + 1;
            Submission submission = submissions.save(new Submission(submissionId, a, attempt, headSha, tagRef,
                    trigger, Instant.now()));
            a.transitionTo(AssignmentStatus.SUBMITTED);
            audit.record(a, "SUBMISSION_FROZEN", actor, "%s sha=%s trigger=%s".formatted(submissionId, headSha, trigger));
            events.publishEvent(new SubmissionFrozen(assignmentId, submissionId));
            return submission;
        });
    }

    private Assignment load(String assignmentId) {
        return assignments.findByPublicId(assignmentId)
                .orElseThrow(() -> new NotFoundException("Assignment", assignmentId));
    }
}
