package com.geekyants.assessment.webhook;

import com.geekyants.assessment.github.GithubRepo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CiRunRepository extends JpaRepository<CiRun, Long> {

    Optional<CiRun> findByGithubRunId(long githubRunId);

    List<CiRun> findTop10ByRepositoryOrderByUpdatedAtDesc(GithubRepo repository);
}
