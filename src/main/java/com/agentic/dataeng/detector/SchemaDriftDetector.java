package com.agentic.dataeng.detector;

import com.agentic.dataeng.model.DriftReport;
import com.agentic.dataeng.model.DriftReport.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class SchemaDriftDetector {

    private static final Logger log =
            LoggerFactory.getLogger(SchemaDriftDetector.class);

    private final ObjectMapper objectMapper;

    public SchemaDriftDetector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<DriftReport> detectDrift(
            String pipelineId,
            String topic,
            JsonNode baselineSchema,
            JsonNode incomingEvent) {

        List<FieldDelta> deltas = new ArrayList<>();

        Set<String> baselineFields = new HashSet<>();
        baselineSchema.fieldNames()
                .forEachRemaining(baselineFields::add);

        Set<String> incomingFields = new HashSet<>();
        incomingEvent.fieldNames()
                .forEachRemaining(incomingFields::add);

        // =========================================================
        // 1. DETECT NEW FIELDS
        // =========================================================

        for (String field : incomingFields) {

            if (!baselineFields.contains(field)) {

                JsonNode value = incomingEvent.get(field);

                String detectedType = mapJsonNodeType(value);

                deltas.add(
                        FieldDelta.builder()
                                .fieldName(field)
                                .deltaType(DeltaType.FIELD_ADDED)
                                .sourceDataType(null)
                                .incomingDataType(detectedType)
                                .breaking(false)
                                .recommendedSqlType(
                                        mapToPostgreSqlType(detectedType)
                                )
                                .explanation(
                                        "New optional field '" + field
                                                + "' introduced in stream. "
                                                + "Backward-compatible."
                                )
                                .build()
                );
            }
        }

        // =========================================================
        // 2. DETECT MISSING FIELDS
        // =========================================================

        for (String field : baselineFields) {

            if (!incomingFields.contains(field)) {

                JsonNode expectedNode =
                        baselineSchema.get(field);

                String expectedType =
                        getBaselineType(expectedNode);

                deltas.add(
                        FieldDelta.builder()
                                .fieldName(field)
                                .deltaType(DeltaType.FIELD_REMOVED)
                                .sourceDataType(expectedType)
                                .incomingDataType(null)
                                .breaking(true)
                                .explanation(
                                        "Mandatory field '" + field
                                                + "' is missing from payload."
                                )
                                .build()
                );
            }
        }

        // =========================================================
        // 3. DETECT TYPE CHANGES
        // =========================================================

        for (String field : baselineFields) {

            if (!incomingFields.contains(field)) {
                continue;
            }

            JsonNode expectedNode =
                    baselineSchema.get(field);

            JsonNode actualNode =
                    incomingEvent.get(field);

            if (actualNode == null || actualNode.isNull()) {
                continue;
            }

            String expectedType =
                    getBaselineType(expectedNode);

            String actualType =
                    mapJsonNodeType(actualNode);

            if (!expectedType.equalsIgnoreCase(actualType)) {

                boolean isCoercible =
                        canCoerce(
                                expectedType,
                                actualType,
                                actualNode.asText()
                        );

                deltas.add(
                        FieldDelta.builder()
                                .fieldName(field)
                                .deltaType(DeltaType.TYPE_CHANGED)
                                .sourceDataType(expectedType)
                                .incomingDataType(actualType)
                                .breaking(!isCoercible)
                                .recommendedSqlType(
                                        mapToPostgreSqlType(actualType)
                                )
                                .explanation(
                                        "Type mismatch on '" + field
                                                + "': expected "
                                                + expectedType
                                                + ", got "
                                                + actualType
                                )
                                .build()
                );
            }
        }

        // =========================================================
        // 4. NO DRIFT
        // =========================================================

        if (deltas.isEmpty()) {
            return Optional.empty();
        }

        // =========================================================
        // 5. DETERMINE SEVERITY
        // =========================================================

        boolean hasBreaking =
                deltas.stream()
                        .anyMatch(FieldDelta::isBreaking);

        boolean hasTypeChange =
                deltas.stream()
                        .anyMatch(
                                d -> d.getDeltaType()
                                        == DeltaType.TYPE_CHANGED
                        );

        DriftSeverity severity;

        if (hasBreaking) {
            severity = DriftSeverity.HIGH_BREAKING;
        } else if (hasTypeChange) {
            severity = DriftSeverity.MEDIUM_COERCIBLE;
        } else {
            severity = DriftSeverity.LOW_ADDITIVE;
        }

        // =========================================================
        // 6. DETERMINE COMPATIBILITY
        // =========================================================

        CompatibilityMode mode;

        if (hasBreaking) {
            mode = CompatibilityMode.INCOMPATIBLE;
        } else if (severity == DriftSeverity.LOW_ADDITIVE) {
            mode = CompatibilityMode.BACKWARD;
        } else {
            mode = CompatibilityMode.FORWARD;
        }

        // =========================================================
        // 7. DETERMINE CATEGORY
        // =========================================================

        boolean hasMissingField =
                deltas.stream()
                        .anyMatch(
                                d -> d.getDeltaType()
                                        == DeltaType.FIELD_REMOVED
                        );

        boolean hasTypeMutation =
                deltas.stream()
                        .anyMatch(
                                d -> d.getDeltaType()
                                        == DeltaType.TYPE_CHANGED
                        );

        boolean hasNewField =
                deltas.stream()
                        .anyMatch(
                                d -> d.getDeltaType()
                                        == DeltaType.FIELD_ADDED
                        );

        DriftCategory category;

        if (hasMissingField) {
            category = DriftCategory.MANDATORY_FIELD_MISSING;
        } else if (hasTypeMutation) {
            category = DriftCategory.TYPE_MUTATION;
        } else if (hasNewField) {
            category = DriftCategory.NEW_OPTIONAL_FIELD;
        } else {
            category = DriftCategory.TYPE_MUTATION;
        }

        // =========================================================
        // 8. BUILD REPORT
        // =========================================================

        DriftReport report =
                DriftReport.builder()
                        .pipelineId(pipelineId)
                        .topicName(topic)
                        .currentSchemaVersion(1)
                        .detectedAt(Instant.now())
                        .severity(severity)
                        .category(category)
                        .compatibilityMode(mode)
                        .fieldDeltas(deltas)
                        .rawDriftedSample(
                                incomingEvent.toPrettyString()
                        )
                        .requiresHumanReview(hasBreaking)
                        .isAutoHealable(!hasBreaking)
                        .build();

        // =========================================================
        // 9. LOG
        // =========================================================

        log.warn(
                ">>> [SCHEMA DRIFT ALERT] Pipeline: {} | Severity: {} | Deltas: {}",
                pipelineId,
                severity,
                deltas.size()
        );

        return Optional.of(report);
    }

    // =============================================================
    // BASELINE TYPE DETECTION
    // =============================================================

    private String getBaselineType(JsonNode node) {

        if (node == null || node.isNull()) {
            return "null";
        }

        /*
         * The tests use actual sample data as the baseline:
         *
         * "ORD-101"  -> string
         * 99.50      -> double
         * 120        -> integer
         *
         * But the application may also send schema descriptors:
         *
         * "integer"
         * "double"
         * "boolean"
         * "string"
         *
         * Therefore only recognized datatype names are treated
         * as datatype descriptors. Normal text is treated as string.
         */

        if (node.isTextual()) {

            String value = node.asText().trim().toLowerCase();

            switch (value) {

                case "string":
                case "text":
                    return "string";

                case "integer":
                case "int":
                case "long":
                    return "integer";

                case "double":
                case "float":
                case "decimal":
                case "number":
                    return "double";

                case "boolean":
                case "bool":
                    return "boolean";

                case "array":
                    return "array";

                case "object":
                    return "object";

                default:
                    // Actual sample such as "ORD-101"
                    // or "CONFIRMED"
                    return "string";
            }
        }

        return mapJsonNodeType(node);
    }

    // =============================================================
    // COERCION LOGIC
    // =============================================================

    private boolean canCoerce(
            String expected,
            String actual,
            String rawValue) {

        expected = expected.toLowerCase();
        actual = actual.toLowerCase();

        // Same type
        if (expected.equals(actual)) {
            return true;
        }

        // String -> Integer
        if ("integer".equals(expected)
                && "string".equals(actual)) {

            try {
                Integer.parseInt(rawValue);
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        // String -> Double
        if ("double".equals(expected)
                && "string".equals(actual)) {

            try {
                Double.parseDouble(rawValue);
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        // Integer -> Double
        if ("double".equals(expected)
                && "integer".equals(actual)) {

            return true;
        }

        // Integer -> String
        if ("string".equals(expected)
                && "integer".equals(actual)) {

            return true;
        }

        // Double -> String
        if ("string".equals(expected)
                && "double".equals(actual)) {

            return true;
        }

        // Boolean -> String
        if ("string".equals(expected)
                && "boolean".equals(actual)) {

            return true;
        }

        return false;
    }

    // =============================================================
    // JSON TYPE DETECTION
    // =============================================================

    private String mapJsonNodeType(JsonNode node) {

        if (node == null || node.isNull()) {
            return "null";
        }

        if (node.isIntegralNumber()) {
            return "integer";
        }

        if (node.isFloatingPointNumber()) {
            return "double";
        }

        if (node.isBoolean()) {
            return "boolean";
        }

        if (node.isArray()) {
            return "array";
        }

        if (node.isObject()) {
            return "object";
        }

        return "string";
    }

    // =============================================================
    // POSTGRESQL TYPE MAPPING
    // =============================================================

    private String mapToPostgreSqlType(String detectedType) {

        return switch (detectedType.toLowerCase()) {

            case "integer" -> "BIGINT";

            case "double" -> "NUMERIC(14,2)";

            case "boolean" -> "BOOLEAN";

            case "object", "array" -> "JSONB";

            default -> "VARCHAR(255)";
        };
    }
}