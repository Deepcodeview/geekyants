package com.geekyants.assessment.audit;

import com.geekyants.assessment.assignment.Assignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByAssignmentOrderByCreatedAtAsc(Assignment assignment);
}
