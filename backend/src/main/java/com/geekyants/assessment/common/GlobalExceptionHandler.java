package com.geekyants.assessment.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Enterprise centralized exception handler.
 * Produces structured, consistent error envelopes with field validation details and trace IDs.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("Validation failed for {}: {}", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorEnvelope(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_FAILED",
                "Request validation failed. Please check the 'fieldErrors' object.",
                request.getRequestURI(),
                getTraceId(),
                Instant.now(),
                fieldErrors
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorEnvelope> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Bad request on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorEnvelope(
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId(),
                Instant.now(),
                null
        ));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorEnvelope> handleConflict(ConflictException ex, HttpServletRequest request) {
        log.warn("Conflict on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorEnvelope(
                HttpStatus.CONFLICT.value(),
                "CONFLICT",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId(),
                Instant.now(),
                null
        ));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        log.info("Resource not found on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorEnvelope(
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId(),
                Instant.now(),
                null
        ));
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorEnvelope> handleExternalService(ExternalServiceException ex, HttpServletRequest request) {
        log.error("External service dependency failure on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorEnvelope(
                HttpStatus.BAD_GATEWAY.value(),
                "BAD_GATEWAY",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId(),
                Instant.now(),
                null
        ));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorEnvelope> handleNoSuchElement(NoSuchElementException ex, HttpServletRequest request) {
        log.info("Element not found on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorEnvelope(
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                getTraceId(),
                Instant.now(),
                null
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorEnvelope(
                HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN",
                "Access is denied. You lack the necessary permissions for this resource.",
                request.getRequestURI(),
                getTraceId(),
                Instant.now(),
                null
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorEnvelope(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected internal error occurred. Please reference the trace ID.",
                request.getRequestURI(),
                getTraceId(),
                Instant.now(),
                null
        ));
    }

    private String getTraceId() {
        String id = MDC.get(TraceIdFilter.MDC_KEY);
        return id != null ? id : "N/A";
    }

    public record ErrorEnvelope(
            int status,
            String error,
            String message,
            String path,
            String traceId,
            Instant timestamp,
            Map<String, String> fieldErrors
    ) {
    }
}
