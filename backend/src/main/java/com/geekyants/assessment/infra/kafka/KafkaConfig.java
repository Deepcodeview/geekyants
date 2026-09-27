package com.geekyants.assessment.infra.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Enterprise Apache Kafka topic and broker configuration.
 * Topics are partitioned for parallel distributed candidate assessment processing.
 */
@Configuration
@EnableKafka
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    public static final String TOPIC_PIPELINE_EVENTS = "assessment.pipeline.events";
    public static final String TOPIC_AUDIT_EVENTS = "assessment.audit.events";
    public static final String TOPIC_EVALUATION_RUNS = "assessment.evaluation.runs";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 2);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public org.springframework.kafka.core.ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, "assessment-platform-pipeline-group");
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class);
        props.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            org.springframework.kafka.core.ConsumerFactory<String, String> consumerFactory) {
        org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(2);
        return factory;
    }

    @Bean
    public NewTopic pipelineEventsTopic() {
        log.info("Registering Kafka topic: {}", TOPIC_PIPELINE_EVENTS);
        return TopicBuilder.name(TOPIC_PIPELINE_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auditEventsTopic() {
        log.info("Registering Kafka topic: {}", TOPIC_AUDIT_EVENTS);
        return TopicBuilder.name(TOPIC_AUDIT_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic evaluationRunsTopic() {
        log.info("Registering Kafka topic: {}", TOPIC_EVALUATION_RUNS);
        return TopicBuilder.name(TOPIC_EVALUATION_RUNS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
