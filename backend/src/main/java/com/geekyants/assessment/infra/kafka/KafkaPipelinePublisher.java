package com.geekyants.assessment.infra.kafka;

import com.geekyants.assessment.assignment.PipelineEvents;
import com.geekyants.assessment.common.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event bridge that intercepts all domain lifecycle events and publishes them to Apache Kafka.
 * Enables decoupled microservice event streaming, monitoring, and async execution.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaPipelinePublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaPipelinePublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Json json;

    public KafkaPipelinePublisher(org.springframework.beans.factory.ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider, Json json) {
        this.kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        this.json = json;
    }

    @Async
    @EventListener
    public void onRequirementAnalysisRequested(PipelineEvents.RequirementAnalysisRequested event) {
        publish(event.assignmentId(), "REQUIREMENT_ANALYSIS_REQUESTED", event);
    }

    @Async
    @EventListener
    public void onBlueprintRequested(PipelineEvents.BlueprintRequested event) {
        publish(event.assignmentId(), "BLUEPRINT_REQUESTED", event);
    }

    @Async
    @EventListener
    public void onProvisioningRequested(PipelineEvents.ProvisioningRequested event) {
        publish(event.assignmentId(), "PROVISIONING_REQUESTED", event);
    }

    @Async
    @EventListener
    public void onCandidateAccessRequested(PipelineEvents.CandidateAccessRequested event) {
        publish(event.assignmentId(), "CANDIDATE_ACCESS_REQUESTED", event);
    }

    @Async
    @EventListener
    public void onSubmissionFrozen(PipelineEvents.SubmissionFrozen event) {
        publish(event.assignmentId(), "SUBMISSION_FROZEN", event);
    }

    @Async
    @EventListener
    public void onEvaluationCompleted(PipelineEvents.EvaluationCompleted event) {
        publish(event.assignmentId(), "EVALUATION_COMPLETED", event);
    }

    @Async
    @EventListener
    public void onCodeAnalysisCompleted(PipelineEvents.CodeAnalysisCompleted event) {
        publish(event.assignmentId(), "CODE_ANALYSIS_COMPLETED", event);
    }

    @Async
    @EventListener
    public void onMcqCompleted(PipelineEvents.McqCompleted event) {
        publish(event.assignmentId(), "MCQ_COMPLETED", event);
    }

    private void publish(String key, String eventType, Object payload) {
        if (kafkaTemplate == null) {
            return;
        }
        try {
            String message = json.write(new KafkaEventEnvelope(eventType, key, payload, java.time.Instant.now()));
            kafkaTemplate.send(KafkaConfig.TOPIC_PIPELINE_EVENTS, key, message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Kafka dispatch skipped for {} ({}): broker unavailable", eventType, key);
                        } else {
                            log.info("Published to Kafka [{}]: key={} offset={}",
                                    KafkaConfig.TOPIC_PIPELINE_EVENTS, key, result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.warn("Could not serialize Kafka event {}: {}", eventType, e.getMessage());
        }
    }

    public record KafkaEventEnvelope(String type, String assignmentId, Object payload, java.time.Instant timestamp) {
    }
}
