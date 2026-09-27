package com.geekyants.assessment.infra.kafka;

import com.geekyants.assessment.audit.AuditEvent;
import com.geekyants.assessment.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Streams audit and system events to Kafka for real-time observability and enterprise monitoring.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaAuditStreamer {

    private static final Logger log = LoggerFactory.getLogger(KafkaAuditStreamer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Json json;

    public KafkaAuditStreamer(org.springframework.beans.factory.ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider, Json json) {
        this.kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        this.json = json;
    }

    public void streamAuditEvent(AuditEvent event) {
        if (kafkaTemplate == null) {
            return;
        }
        try {
            String payload = json.write(new AuditEnvelope(
                    String.valueOf(event.getId()),
                    event.getAssignment() != null ? event.getAssignment().getPublicId() : "SYSTEM",
                    event.getEventType(),
                    event.getActor(),
                    event.getDetail(),
                    event.getCreatedAt()
            ));
            kafkaTemplate.send(KafkaConfig.TOPIC_AUDIT_EVENTS, event.getEventType(), payload);
        } catch (Exception e) {
            log.debug("Kafka audit stream skipped (broker offline): {}", e.getMessage());
        }
    }

    public record AuditEnvelope(String auditId, String assignmentId, String eventType, String actor, String detail, java.time.Instant at) {
    }
}
