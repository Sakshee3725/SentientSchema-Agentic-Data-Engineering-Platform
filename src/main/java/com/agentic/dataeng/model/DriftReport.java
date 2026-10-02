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
public class DriftReport {

    private String pipelineId;
    private String topicName;
    private int currentSchemaVersion;
    private Instant detectedAt;

    private DriftSeverity severity;
    private DriftCategory category;
    private CompatibilityMode compatibilityMode;

    private List<FieldDelta> fieldDeltas;
    private String rawDriftedSample;
    private boolean requiresHumanReview;
    private boolean isAutoHealable;

    public enum DriftSeverity {
        LOW_ADDITIVE,
        MEDIUM_COERCIBLE,
        HIGH_BREAKING,
        CRITICAL_CORRUPTED
    }

    public enum DriftCategory {
        NEW_OPTIONAL_FIELD,
        TYPE_MUTATION,
        FIELD_RENAMED,
        MANDATORY_FIELD_MISSING,
        NESTED_STRUCTURE_CHANGE,
        NULLABILITY_VIOLATION
    }

    public enum CompatibilityMode {
        BACKWARD,
        FORWARD,
        FULL,
        INCOMPATIBLE
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDelta {

        private String fieldName;
        private DeltaType deltaType;
        private String sourceDataType;
        private String incomingDataType;
        private boolean breaking;
        private String recommendedSqlType;
        private String explanation;
    }

    public enum DeltaType {
        FIELD_ADDED,
        FIELD_REMOVED,
        TYPE_CHANGED,
        CONSTRAINT_CHANGED,
        STRUCTURAL_RENAME
    }
}