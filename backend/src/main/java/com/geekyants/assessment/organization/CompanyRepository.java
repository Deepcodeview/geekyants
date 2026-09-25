package com.geekyants.assessment.organization;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByExternalId(String externalId);
}
