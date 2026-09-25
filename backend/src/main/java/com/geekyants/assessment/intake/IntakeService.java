package com.geekyants.assessment.intake;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.assignment.AssignmentRepository;
import com.geekyants.assessment.assignment.AssignmentStatus;
import com.geekyants.assessment.assignment.PipelineEvents.RequirementAnalysisRequested;
import com.geekyants.assessment.audit.AuditLog;
import com.geekyants.assessment.common.ConflictException;
import com.geekyants.assessment.common.Ids;
import com.geekyants.assessment.common.Json;
import com.geekyants.assessment.config.AppProperties;
import com.geekyants.assessment.organization.Candidate;
import com.geekyants.assessment.organization.CandidateProfile;
import com.geekyants.assessment.organization.CandidateRepository;
import com.geekyants.assessment.organization.Company;
import com.geekyants.assessment.organization.CompanyProfile;
import com.geekyants.assessment.organization.CompanyRepository;
import com.geekyants.assessment.organization.Job;
import com.geekyants.assessment.organization.JobProfile;
import com.geekyants.assessment.organization.JobRepository;
import java.util.EnumSet;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Upserts company, job and candidate data from the recruitment system and opens an assignment. */
@Service
public class IntakeService {

    private static final EnumSet<AssignmentStatus> CLOSED = EnumSet.of(AssignmentStatus.REPORTED);

    private final CompanyRepository companies;
    private final JobRepository jobs;
    private final CandidateRepository candidates;
    private final AssignmentRepository assignments;
    private final AuditLog audit;
    private final ApplicationEventPublisher events;
    private final Json json;
    private final AppProperties props;

    public IntakeService(CompanyRepository companies, JobRepository jobs, CandidateRepository candidates,
                         AssignmentRepository assignments, AuditLog audit, ApplicationEventPublisher events,
                         Json json, AppProperties props) {
        this.companies = companies;
        this.jobs = jobs;
        this.candidates = candidates;
        this.assignments = assignments;
        this.audit = audit;
        this.events = events;
        this.json = json;
        this.props = props;
    }

    @Transactional
    public Assignment receive(IntakeRequest request, String actor) {
        Company company = upsertCompany(request.company());
        Job job = upsertJob(company, request.job());
        Candidate candidate = upsertCandidate(request.candidate());

        if (assignments.existsByJobIdAndCandidateIdAndStatusNotIn(job.getId(), candidate.getId(), CLOSED)) {
            throw new ConflictException("Candidate %s already has an open assignment for job %s"
                    .formatted(candidate.getExternalId(), job.getExternalId()));
        }

        int duration = request.durationDays() != null ? request.durationDays()
                : props.assessment().defaultDurationDays();
        Assignment assignment = assignments.save(
                new Assignment(Ids.next(Ids.ASSIGNMENT), company, job, candidate, duration));
        audit.record(assignment, "ASSIGNMENT_CREATED", actor, "job=%s candidate=%s"
                .formatted(job.getExternalId(), candidate.getExternalId()));

        assignment.transitionTo(AssignmentStatus.ANALYZING_REQUIREMENTS);
        events.publishEvent(new RequirementAnalysisRequested(assignment.getPublicId()));
        return assignment;
    }

    private Company upsertCompany(CompanyProfile profile) {
        Company company = companies.findByExternalId(profile.companyId())
                .orElseGet(() -> new Company(profile.companyId()));
        company.update(profile.name(), profile.industry(), profile.domain(), json.write(profile));
        return companies.save(company);
    }

    private Job upsertJob(Company company, JobProfile profile) {
        Job job = jobs.findByCompanyAndExternalId(company, profile.jobId())
                .orElseGet(() -> new Job(company, profile.jobId()));
        job.update(profile.title(), profile.department(), profile.seniority(), profile.requiredExperience(),
                json.write(profile));
        return jobs.save(job);
    }

    private Candidate upsertCandidate(CandidateProfile profile) {
        Candidate candidate = candidates.findByExternalId(profile.candidateId())
                .orElseGet(() -> new Candidate(profile.candidateId()));
        candidate.update(profile.fullName(), profile.email(), json.write(profile));
        if (profile.githubUsername() != null && candidate.getGithubUsername() == null) {
            candidate.linkGithubUsername(profile.githubUsername());
        }
        return candidates.save(candidate);
    }
}
