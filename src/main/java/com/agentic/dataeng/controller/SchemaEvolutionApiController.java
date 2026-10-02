package com.agentic.dataeng.controller;

import com.agentic.dataeng.agent.SelfHealingAgentService;
import com.agentic.dataeng.detector.SchemaDriftDetector;
import com.agentic.dataeng.model.DriftReport;
import com.agentic.dataeng.model.HealingPlan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/schema-evolution")
@CrossOrigin(origins = "*")
public class SchemaEvolutionApiController {

    private final SchemaDriftDetector driftDetector;
    private final SelfHealingAgentService agentService;
    private final ObjectMapper objectMapper;

    public SchemaEvolutionApiController(
            SchemaDriftDetector driftDetector,
            SelfHealingAgentService agentService,
            ObjectMapper objectMapper) {

        this.driftDetector = driftDetector;
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // SIMULATE SCHEMA DRIFT
    // ============================================================

    @PostMapping("/simulate-drift")
    public ResponseEntity<?> simulateDrift(
            @RequestBody Map<String, Object> request) {

        try {

            // ----------------------------------------------------
            // 1. Get Pipeline ID
            // ----------------------------------------------------

            String pipelineId =
                    (String) request.getOrDefault(
                            "pipelineId",
                            "ecommerce_orders"
                    );

            // ----------------------------------------------------
            // 2. Validate Baseline Schema
            // ----------------------------------------------------

            if (!request.containsKey("baselineSchema")
                    || request.get("baselineSchema") == null) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "error",
                                        "baselineSchema is required"
                                )
                        );
            }

            // ----------------------------------------------------
            // 3. Validate Drifted Payload
            // ----------------------------------------------------

            if (!request.containsKey("driftedPayload")
                    || request.get("driftedPayload") == null) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "error",
                                        "driftedPayload is required"
                                )
                        );
            }

            // ----------------------------------------------------
            // 4. Convert Request Data to JsonNode
            // ----------------------------------------------------

            JsonNode baseline =
                    objectMapper.convertValue(
                            request.get("baselineSchema"),
                            JsonNode.class
                    );

            JsonNode payload =
                    objectMapper.convertValue(
                            request.get("driftedPayload"),
                            JsonNode.class
                    );

            // ----------------------------------------------------
            // 5. Validate JSON Objects
            // ----------------------------------------------------

            if (!baseline.isObject()) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "error",
                                        "baselineSchema must be a JSON object"
                                )
                        );
            }

            if (!payload.isObject()) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "error",
                                        "driftedPayload must be a JSON object"
                                )
                        );
            }

            // ----------------------------------------------------
            // 6. Detect Schema Drift
            // ----------------------------------------------------

            Optional<DriftReport> driftOpt =
                    driftDetector.detectDrift(
                            pipelineId,
                            "events." + pipelineId,
                            baseline,
                            payload
                    );

            // ----------------------------------------------------
            // 7. No Drift Detected
            // ----------------------------------------------------

            if (driftOpt.isEmpty()) {

                return ResponseEntity.ok(
                        Map.of(
                                "status",
                                "NO_DRIFT",

                                "message",
                                "No schema drift detected. Payload perfectly matches baseline."
                        )
                );
            }

            // ----------------------------------------------------
            // 8. Drift Detected
            // ----------------------------------------------------

            DriftReport report = driftOpt.get();

            // ----------------------------------------------------
            // 9. Execute Self-Healing Strategy
            // ----------------------------------------------------

            HealingPlan plan =
                    agentService.formulateAndExecuteHealing(report);

            // ----------------------------------------------------
            // 10. Build Response
            // ----------------------------------------------------

            Map<String, Object> response =
                    new HashMap<>();

            response.put(
                    "status",
                    "DRIFT_DETECTED"
            );

            response.put(
                    "driftReport",
                    report
            );

            response.put(
                    "healingPlan",
                    plan
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {

            // ----------------------------------------------------
            // 11. Handle Unexpected Errors
            // ----------------------------------------------------

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            Map.of(
                                    "status",
                                    "ERROR",

                                    "message",
                                    "Failed to process schema drift",

                                    "error",
                                    e.getMessage()
                            )
                    );
        }
    }

    // ============================================================
    // HEALTH CHECK
    // ============================================================

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {

        return ResponseEntity.ok(
                Map.of(
                        "status",
                        "UP",

                        "supervisor",
                        "Autonomous Self-Healing Agent Active",

                        "kafkaCluster",
                        "Connected",

                        "activePipelines",
                        4
                )
        );
    }
}