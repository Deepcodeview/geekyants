package com.geekyants.assessment.submission;

import com.geekyants.assessment.assignment.Assignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @EntityGraph(attributePaths = {"assignment"})
    Optional<Submission> findByPublicId(String publicId);

    /** The latest submission is the one evaluated. */
    Optional<Submission> findFirstByAssignmentOrderByAttemptNoDesc(Assignment assignment);

    List<Submission> findByAssignmentOrderByAttemptNoAsc(Assignment assignment);

    int countByAssignment(Assignment assignment);
}
