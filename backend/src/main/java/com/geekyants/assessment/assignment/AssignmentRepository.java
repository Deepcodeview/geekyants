package com.geekyants.assessment.assignment;

import com.geekyants.assessment.organization.Candidate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    @EntityGraph(attributePaths = {"company", "job", "candidate"})
    Optional<Assignment> findByPublicId(String publicId);

    @EntityGraph(attributePaths = {"company", "job", "candidate"})
    Page<Assignment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"company", "job", "candidate"})
    List<Assignment> findByCandidateOrderByCreatedAtDesc(Candidate candidate);

    boolean existsByJobIdAndCandidateIdAndStatusNotIn(Long jobId, Long candidateId,
                                                      Collection<AssignmentStatus> statuses);

    List<Assignment> findByStatusInAndDueAtBefore(Collection<AssignmentStatus> statuses, Instant cutoff);
}
