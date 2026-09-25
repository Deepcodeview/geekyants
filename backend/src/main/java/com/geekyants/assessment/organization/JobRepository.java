package com.geekyants.assessment.organization;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findByCompanyAndExternalId(Company company, String externalId);
}
