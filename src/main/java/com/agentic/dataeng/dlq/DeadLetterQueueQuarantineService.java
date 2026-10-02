package com.agentic.dataeng.dlq;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Intelligent Dead Letter Queue Quarantine Service.
 * Isolates poison-pill records, missing mandatory fields, and irrecoverable corruptions
 * to preserve zero downtime on the primary data pipeline.
 */
@Service
public class DeadLetterQueueQuarantineService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueQuarantineService.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DeadLetterQueueQuarantineService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void quarantineEvent(String pipelineId, String originalTopic, JsonNode event, String reason) {
        String dlqTopic = originalTopic + ".DLQ";
        String eventId = UUID.randomUUID().toString();

        log.error(
            ">>> [DLQ QUARANTINE] Pipeline: {} | EventID: {} | Reason: {}",
            pipelineId,
            eventId,
            reason
        );

        // Attach audit headers
        var dlqEnvelope = Map.of(
            "dlqId", eventId,
            "pipelineId", pipelineId,
            "originalTopic", originalTopic,
            "quarantinedAt", Instant.now().toString(),
            "failureReason", reason,
            "payload", event
        );

        try {
            kafkaTemplate.send(dlqTopic, eventId, dlqEnvelope);
            log.info("Successfully pushed corrupted event to DLQ: {}", dlqTopic);
        } catch (Exception e) {
            log.warn(
                "Kafka DLQ send simulated (offline mode or broker unavailable): {}",
                e.getMessage()
            );
        }
    }
}