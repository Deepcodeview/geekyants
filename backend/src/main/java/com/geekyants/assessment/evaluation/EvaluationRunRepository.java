package com.geekyants.assessment.evaluation;

import com.geekyants.assessment.submission.Submission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, Long> {

    @EntityGraph(attributePaths = {"submission", "submission.assignment"})
    Optional<EvaluationRun> findByPublicId(String publicId);

    List<EvaluationRun> findBySubmissionAndStatus(Submission submission, EvaluationRun.Status status);

    Optional<EvaluationRun> findFirstBySubmissionAndStatusOrderByDispatchedAtDesc(Submission submission,
                                                                                  EvaluationRun.Status status);

    @EntityGraph(attributePaths = {"submission", "submission.assignment"})
    List<EvaluationRun> findByStatusAndDispatchedAtBefore(EvaluationRun.Status status, Instant cutoff);
}
