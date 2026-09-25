package com.geekyants.assessment.webhook;

import com.geekyants.assessment.common.HmacSignatures;
import com.geekyants.assessment.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/webhooks/github")
class GitHubWebhookController {

    private final GitHubWebhookService service;
    private final ObjectMapper mapper;
    private final String secret;

    GitHubWebhookController(GitHubWebhookService service, ObjectMapper mapper, AppProperties props) {
        this.service = service;
        this.mapper = mapper;
        this.secret = props.github().webhookSecret();
    }

    @PostMapping
    ResponseEntity<Void> receive(@RequestHeader("X-GitHub-Event") String event,
                                 @RequestHeader("X-GitHub-Delivery") String deliveryId,
                                 @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                                 @RequestBody byte[] body) {
        // Verify against the raw bytes before parsing anything.
        if (!HmacSignatures.verify(secret, body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if ("ping".equals(event)) {
            return ResponseEntity.noContent().build();
        }
        JsonNode payload = mapper.readTree(body);
        service.handle(event, deliveryId, payload);
        return ResponseEntity.accepted().build();
    }
}
