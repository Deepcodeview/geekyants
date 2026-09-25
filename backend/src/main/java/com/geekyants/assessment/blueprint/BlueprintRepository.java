package com.geekyants.assessment.blueprint;

import com.geekyants.assessment.assignment.Assignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlueprintRepository extends JpaRepository<Blueprint, Long> {

    List<Blueprint> findByAssignmentOrderByVersionNoDesc(Assignment assignment);

    Optional<Blueprint> findFirstByAssignmentOrderByVersionNoDesc(Assignment assignment);

    Optional<Blueprint> findByAssignmentAndStatus(Assignment assignment, BlueprintStatus status);

    Optional<Blueprint> findByPublicId(String publicId);
}
