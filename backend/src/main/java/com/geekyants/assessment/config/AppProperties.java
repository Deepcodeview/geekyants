package com.geekyants.assessment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String publicBaseUrl,
        java.util.List<String> corsAllowedOrigins,
        Security security,
        Github github,
        Evaluation evaluation,
        Llm llm,
        Assessment assessment) {

    public record Security(String rolesClaim, String candidateIdClaim, String devJwtSecret) {
    }

    public record Github(
            String apiUrl,
            String organization,
            String token,
            String webhookSecret,
            String evaluationRepo,
            String evaluationWorkflow,
            String evaluationRef,
            boolean enforceRulesets) {
    }

    public record Evaluation(String callbackSecret) {
    }

    public record Llm(String model, long maxTokens, boolean refusalFallbacks) {
    }

    public record Assessment(
            int defaultDurationDays,
            int mcqMinQuestions,
            int mcqMaxQuestions,
            int mcqTimeLimitMinutes,
            int analysisMaxSourceChars) {
    }
}
