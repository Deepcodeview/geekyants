package com.geekyants.assessment.github;

import com.geekyants.assessment.assignment.Assignment;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubRepoRepository extends JpaRepository<GithubRepo, Long> {

    Optional<GithubRepo> findByAssignment(Assignment assignment);

    @EntityGraph(attributePaths = {"assignment"})
    Optional<GithubRepo> findByOwnerIgnoreCaseAndNameIgnoreCase(String owner, String name);
}
