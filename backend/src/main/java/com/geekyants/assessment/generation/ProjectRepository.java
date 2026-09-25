package com.geekyants.assessment.generation;

import com.geekyants.assessment.assignment.Assignment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByAssignment(Assignment assignment);
}
