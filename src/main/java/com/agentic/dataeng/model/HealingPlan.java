package com.agentic.dataeng.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealingPlan {
    private String planId;
    private String pipelineId;
    private Strategy strategy;
    private boolean executedSuccessfully;
    private String generatedSqlDdl;
    private List<String> reasoningTrace;
    private int targetSchemaVersion;
    private Instant executedAt;

    public enum Strategy {
        DYNAMIC_FLYWAY_MIGRATION,
        IN_FLIGHT_COERCION,
        DEAD_LETTER_QUEUE_QUARANTINE,
        HYBRID_EVOLVE_AND_ADAPT,
        MANUAL_INTERVENTION_REQUIRED
    }
}