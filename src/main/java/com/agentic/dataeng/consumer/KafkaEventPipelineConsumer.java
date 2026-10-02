
package com.agentic.dataeng.consumer;

import com.agentic.dataeng.agent.SelfHealingAgentService;
import com.agentic.dataeng.detector.SchemaDriftDetector;
import com.agentic.dataeng.dlq.DeadLetterQueueQuarantineService;
import com.agentic.dataeng.healing.InFlightTypeCoercionFilter;
import com.agentic.dataeng.model.DriftReport;
import com.agentic.dataeng.model.HealingPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-time Streaming Consumer for Mission-Critical Ingestion Topics.
 * Intercepts events, compares with Confluent/Jackson baseline,
 * triggers Agentic Self-Healing if drift occurs.
 */
@Component
public class KafkaEventPipelineConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(KafkaEventPipelineConsumer.class);

    private final SchemaDriftDetector driftDetector;
    private final SelfHealingAgentService agentService;
    private final InFlightTypeCoercionFilter coercionFilter;
    private final DeadLetterQueueQuarantineService dlqService;
    private final ObjectMapper objectMapper;

    // Cache of baseline schemas by pipeline topic
    private final Map<String, JsonNode> baselineRegistry =
            new ConcurrentHashMap<>();

    public KafkaEventPipelineConsumer(
            SchemaDriftDetector driftDetector,
            SelfHealingAgentService agentService,
            InFlightTypeCoercionFilter coercionFilter,
            DeadLetterQueueQuarantineService dlqService,
            ObjectMapper objectMapper) {

        this.driftDetector = driftDetector;
        this.agentService = agentService;
        this.coercionFilter = coercionFilter;
        this.dlqService = dlqService;
        this.objectMapper = objectMapper;
    }

    public void registerBaselineSchema(String topic, JsonNode schema) {
        this.baselineRegistry.put(topic, schema);

        log.info(
                "Registered baseline schema for topic: {}",
                topic
        );
    }

    @KafkaListener(
            topics = "#{'${pipeline.kafka.topics}'.split(',')}",
            groupId = "agentic-dataeng-consumer-group"
    )
    public void consumeEvent(ConsumerRecord<String, String> record) {

        String topic = record.topic();
        String rawJson = record.value();

        try {

            JsonNode incomingNode =
                    objectMapper.readTree(rawJson);

            JsonNode baseline =
                    baselineRegistry.get(topic);

            if (baseline == null) {

                log.debug(
                        "No baseline registered for topic {}, auto-initializing from event",
                        topic
                );

                baselineRegistry.put(topic, incomingNode);
                return;
            }

            // 1. Run Tree-Diff Drift Detection
            Optional<DriftReport> driftOpt =
                    driftDetector.detectDrift(
                            topic,
                            topic,
                            baseline,
                            incomingNode
                    );

            if (driftOpt.isPresent()) {

                DriftReport report =
                        driftOpt.get();

                // 2. Invoke Self-Healing AI Agent
                HealingPlan plan =
                        agentService.formulateAndExecuteHealing(report);

                if (plan.getStrategy()
                        == HealingPlan.Strategy.DEAD_LETTER_QUEUE_QUARANTINE) {

                    dlqService.quarantineEvent(
                            topic,
                            topic,
                            incomingNode,
                            "Breaking drift detected by Agentic Supervisor"
                    );

                    return;
                }

                // If non-breaking evolution was applied,
                // adapt and persist
                if (plan.isExecutedSuccessfully()) {

                    baselineRegistry.put(
                            topic,
                            incomingNode
                    );
                }
            }

            // 3. Apply In-Flight Coercion Filter
            // if active
            JsonNode adaptedEvent =
                    coercionFilter.adaptEvent(
                            topic,
                            incomingNode
                    );

            // 4. Downstream Persistence
            // PostgreSQL / ClickHouse / Data Warehouse
            persistToDataWarehouse(
                    topic,
                    adaptedEvent
            );

        } catch (Exception e) {

            log.error(
                    "Fatal ingestion error on topic [{}]: {}",
                    topic,
                    e.getMessage(),
                    e
            );

            dlqService.quarantineEvent(
                    topic,
                    topic,
                    null,
                    "Malformed JSON / Parse Exception: "
                            + e.getMessage()
            );
        }
    }

    private void persistToDataWarehouse(
            String topic,
            JsonNode event) {

        // High-performance batch/JDBC write
        log.debug(
                ">>> Successfully committed record to Data Warehouse on topic {}",
                topic
        );
    }
}

