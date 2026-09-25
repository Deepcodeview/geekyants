package com.geekyants.assessment.organization;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    Optional<Candidate> findByExternalId(String externalId);
}
