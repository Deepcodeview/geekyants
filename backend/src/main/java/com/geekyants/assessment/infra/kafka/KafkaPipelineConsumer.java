package com.geekyants.assessment.infra.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Enterprise Apache Kafka distributed worker consumer.
 * Listens to pipeline lifecycle events for asynchronous orchestrations, telemetry, and background processing.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaPipelineConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaPipelineConsumer.class);

    @KafkaListener(topics = KafkaConfig.TOPIC_PIPELINE_EVENTS, groupId = "assessment-platform-pipeline-group")
    public void consumePipelineEvent(ConsumerRecord<String, String> record) {
        log.info("Kafka Consumer received pipeline event [topic={}, partition={}, offset={}, key={}]: {}",
                record.topic(), record.partition(), record.offset(), record.key(), record.value());
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_AUDIT_EVENTS, groupId = "assessment-platform-audit-group")
    public void consumeAuditEvent(ConsumerRecord<String, String> record) {
        log.debug("Kafka Consumer received audit event [offset={}, key={}]: {}",
                record.offset(), record.key(), record.value());
    }
}
