package com.geekyants.assessment.report;

import com.geekyants.assessment.assignment.Assignment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinalReportRepository extends JpaRepository<FinalReport, Long> {

    Optional<FinalReport> findByAssignment(Assignment assignment);
}
