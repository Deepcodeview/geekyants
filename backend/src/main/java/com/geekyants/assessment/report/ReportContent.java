package com.geekyants.assessment.report;

import com.geekyants.assessment.codeanalysis.CodeAnalysisResult;
import com.geekyants.assessment.evaluation.EvaluationResult;
import com.geekyants.assessment.mcq.McqCategory;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The final assessment record. Every section carries the IDs needed to trace it back:
 * requirement → code → tests → commit → pipeline run → question source.
 */
public record ReportContent(
        String reportId,
        Instant generatedAt,
        Candidate candidate,
        Project project,
        Repository repository,
        AutomatedEvaluation automatedEvaluation,
        List<RequirementStatus> requirements,
        CodeQuality codeQuality,
        Mcq mcq,
        Scores scores) {

    public record Candidate(String candidateId, String name, String jobId, String jobTitle, String companyId,
                            String companyName) {
    }

    public record Project(String assignmentId, String projectId, String blueprintId, String name, String domain,
                          String complexity, List<String> technologies) {
    }

    public record Repository(String repositoryId, String fullName, String url, String submissionId,
                             String commitSha, String tagRef, Instant submittedAt, String submittedBy) {
    }

    public record AutomatedEvaluation(String evaluationId, String pipelineUrl, List<EvaluationResult.Stage> stages,
                                      EvaluationResult.TestSummary visibleTests,
                                      EvaluationResult.TestSummary hiddenTests, List<String> failedTests) {
    }

    public record RequirementStatus(String code, String title, boolean mandatory,
                                    CodeAnalysisResult.CoverageStatus status, List<String> implementationRefs,
                                    List<String> testRefs, String notes) {
    }

    public record CodeQuality(String codeAnalysisId, String summary, List<CodeAnalysisResult.AreaAssessment> areas,
                              List<CodeAnalysisResult.Finding> findings, int filesAnalyzed,
                              List<Map<String, String>> filesSkipped) {
    }

    public record Mcq(String assessmentId, int total, int correct, Map<McqCategory, CategoryScore> byCategory,
                      List<QuestionResult> questions) {
    }

    public record CategoryScore(int correct, int total) {
    }

    public record QuestionResult(String code, McqCategory category, String stem, List<String> options,
                                 Integer selectedOption, int correctOption, boolean correct, String sourceRef,
                                 String requirementCode, String explanation) {
    }

    /**
     * Percentages 0-100. {@code overall} is a weighted blend for sorting only; reviewers make the
     * hiring decision. Null when a component could not be measured.
     */
    public record Scores(Integer requirements, Integer automatedEvaluation, Integer codeQuality, Integer mcq,
                         Integer overall, Map<String, Integer> weights) {
    }
}
