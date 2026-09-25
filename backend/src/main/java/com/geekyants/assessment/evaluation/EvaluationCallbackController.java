package com.geekyants.assessment.evaluation;

import com.geekyants.assessment.common.HmacSignatures;
import com.geekyants.assessment.config.AppProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/** Receives results from the evaluation runner, authenticated by an HMAC of the raw body. */
@RestController
@RequestMapping("/api/v1/callbacks/evaluations")
class EvaluationCallbackController {

    private final EvaluationService evaluations;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final String secret;

    EvaluationCallbackController(EvaluationService evaluations, ObjectMapper mapper, Validator validator,
                                 AppProperties props) {
        this.evaluations = evaluations;
        this.mapper = mapper;
        this.validator = validator;
        this.secret = props.evaluation().callbackSecret();
    }

    @PostMapping("/{evaluationId}")
    ResponseEntity<Void> receive(@PathVariable String evaluationId,
                                 @RequestHeader(value = "X-Signature-256", required = false) String signature,
                                 @RequestBody byte[] body) {
        if (!HmacSignatures.verify(secret, body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        EvaluationResult result = mapper.readValue(body, EvaluationResult.class);
        Set<ConstraintViolation<EvaluationResult>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getPropertyPath() + " "
                    + violations.iterator().next().getMessage());
        }
        evaluations.recordResult(evaluationId, result);
        return ResponseEntity.accepted().build();
    }
}
