package com.geekyants.assessment.candidate;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentRepository;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.assignment.PipelineEvents.CandidateAccessRequested;
import com.geekyants.assessment.audit.AuditLog;
import com.geekyants.assessment.common.ConflictException;
import com.geekyants.assessment.common.NotFoundException;
import com.geekyants.assessment.github.GitHubClient;
import com.geekyants.assessment.github.GithubRepo;
import com.geekyants.assessment.github.GithubRepoRepository;
import com.geekyants.assessment.organization.Candidate;
import com.geekyants.assessment.organization.CandidateRepository;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateService {

    private static final Pattern GITHUB_USERNAME = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$");

    private final CandidateRepository candidates;
    private final AssignmentRepository assignments;
    private final GithubRepoRepository repos;
    private final GitHubClient github;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;

    public CandidateService(CandidateRepository candidates, AssignmentRepository assignments,
                            GithubRepoRepository repos, GitHubClient github, AuditLog audit,
                            ApplicationEventPublisher events) {
        this.candidates = candidates;
        this.assignments = assignments;
        this.repos = repos;
        this.github = github;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Candidate candidate(String candidateId) {
        return candidates.findByExternalId(candidateId).orElseThrow(() -> new NotFoundException("Candidate", candidateId));
    }

    @Transactional(readOnly = true)
    public List<Assignment> assignments(String candidateId) {
        return assignments.findByCandidateOrderByCreatedAtDesc(candidate(candidateId));
    }

    /** Returns the assignment only if it belongs to the candidate; otherwise 404, never 403, to avoid probing. */
    @Transactional(readOnly = true)
    public Assignment own(String candidateId, String assignmentId) {
        Assignment a = assignments.findByPublicId(assignmentId)
                .orElseThrow(() -> new NotFoundException("Assignment", assignmentId));
        if (!a.getCandidate().getExternalId().equals(candidateId)) {
            throw new NotFoundException("Assignment", assignmentId);
        }
        return a;
    }

    /**
     * Links the GitHub account that will receive repository access.
     * TODO: replace with GitHub OAuth so the platform verifies the candidate controls the account.
     */
    @Transactional
    public void linkGithubUsername(String candidateId, String username) {
        if (username == null || !GITHUB_USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("Not a valid GitHub username");
        }
        Candidate candidate = candidate(candidateId);
        List<Assignment> mine = assignments.findByCandidateOrderByCreatedAtDesc(candidate);
        boolean accessGranted = mine.stream()
                .flatMap(a -> repos.findByAssignment(a).stream())
                .anyMatch(r -> r.getCandidateAccess() != GithubRepo.Access.NONE);
        if (accessGranted && !username.equalsIgnoreCase(candidate.getGithubUsername())) {
            throw new ConflictException("Repository access was already granted to "
                    + candidate.getGithubUsername() + ". Contact the recruiter to change it.");
        }
        if (!github.userExists(username)) {
            throw new IllegalArgumentException("GitHub user " + username + " does not exist");
        }
        candidate.linkGithubUsername(username);
        for (Assignment a : mine) {
            if (a.getStatus() == AssignmentStatus.AWAITING_GITHUB_USERNAME) {
                audit.record(a, "GITHUB_USERNAME_LINKED", candidateId, username);
                events.publishEvent(new CandidateAccessRequested(a.getPublicId()));
            }
        }
    }
}
