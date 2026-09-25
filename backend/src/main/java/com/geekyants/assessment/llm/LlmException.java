package com.geekyants.assessment.llm;

import com.geekyants.assessment.common.ExternalServiceException;

public class LlmException extends ExternalServiceException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
