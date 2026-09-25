package com.geekyants.assessment.llm;

/** Calls an LLM and returns output conforming to the JSON schema derived from {@code type}. */
public interface LlmClient {

    <T> T generate(LlmRequest request, Class<T> type);

    /**
     * @param task   short label used in logs and errors, e.g. "blueprint"
     * @param system stable instructions for the task
     * @param user   the per-request data
     */
    record LlmRequest(String task, String system, String user) {
    }
}
