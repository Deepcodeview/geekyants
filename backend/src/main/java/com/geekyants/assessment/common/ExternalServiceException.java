package com.geekyants.assessment.common;

/** A call to GitHub, the LLM, or another dependency failed (HTTP 502). */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
