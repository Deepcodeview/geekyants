package com.geekyants.assessment.codeanalysis;

import com.geekyants.assessment.submission.Submission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeAnalysisRepository extends JpaRepository<CodeAnalysis, Long> {

    Optional<CodeAnalysis> findBySubmission(Submission submission);
}
