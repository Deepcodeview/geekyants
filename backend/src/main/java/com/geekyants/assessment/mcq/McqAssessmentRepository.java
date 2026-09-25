package com.geekyants.assessment.mcq;

import com.geekyants.assessment.assignment.Assignment;
import com.geekyants.assessment.submission.Submission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McqAssessmentRepository extends JpaRepository<McqAssessment, Long> {

    Optional<McqAssessment> findBySubmission(Submission submission);

    @EntityGraph(attributePaths = {"questions"})
    Optional<McqAssessment> findFirstByAssignmentOrderByCreatedAtDesc(Assignment assignment);

    @EntityGraph(attributePaths = {"assignment"})
    List<McqAssessment> findByStatusAndDeadlineAtBefore(McqAssessment.Status status, Instant cutoff);
}
