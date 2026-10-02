package com.agentic.dataeng;

import com.agentic.dataeng.detector.SchemaDriftDetector;
import com.agentic.dataeng.model.DriftReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 Verification Suite for Final Year Project Evaluation:
 * Assesses Accuracy, True Positive Rates, and Classification of Drift Categories.
 */
class SchemaDriftDetectorTest {

    private SchemaDriftDetector detector;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        detector = new SchemaDriftDetector(mapper);
    }

    @Test
    @DisplayName("Should detect non-breaking additive drift when new optional field is added")
    void testAdditiveDrift() throws Exception {
        String baselineJson = """
            { "order_id": "ORD-101", "amount": 99.50, "status": "CONFIRMED" }
            """;
        String driftedJson = """
            { "order_id": "ORD-101", "amount": 99.50, "status": "CONFIRMED", "discount_code": "AUTUMN20" }
            """;

        JsonNode baseline = mapper.readTree(baselineJson);
        JsonNode incoming = mapper.readTree(driftedJson);

        Optional<DriftReport> result = detector.detectDrift("ecommerce", "events.orders", baseline, incoming);

        assertTrue(result.isPresent(), "Expected drift report for added field");
        DriftReport report = result.get();
        assertEquals(DriftReport.DriftSeverity.LOW_ADDITIVE, report.getSeverity());
        assertEquals(DriftReport.CompatibilityMode.BACKWARD, report.getCompatibilityMode());
        assertEquals(1, report.getFieldDeltas().size());
        assertEquals("discount_code", report.getFieldDeltas().get(0).getFieldName());
    }

    @Test
    @DisplayName("Should detect breaking drift when mandatory field is dropped")
    void testBreakingMissingField() throws Exception {
        String baselineJson = """
            { "patient_id": "P-992", "systolic_bp": 120, "clinic_code": "CLINIC-A" }
            """;
        String missingFieldJson = """
            { "patient_id": "P-992", "clinic_code": "CLINIC-A" }
            """;

        JsonNode baseline = mapper.readTree(baselineJson);
        JsonNode incoming = mapper.readTree(missingFieldJson);

        Optional<DriftReport> result = detector.detectDrift("healthcare", "events.ehr", baseline, incoming);

        assertTrue(result.isPresent());
        DriftReport report = result.get();
        assertEquals(DriftReport.DriftSeverity.HIGH_BREAKING, report.getSeverity());
        assertTrue(report.isRequiresHumanReview());
        assertEquals(DriftReport.CompatibilityMode.INCOMPATIBLE, report.getCompatibilityMode());
    }

    @Test
    @DisplayName("Should detect type coercion drift when numeric field arrives as string")
    void testTypeCoercion() throws Exception {
        String baselineJson = """
            { "device_id": "DEV-01", "temperature": 24.5 }
            """;
        String coercedJson = """
            { "device_id": "DEV-01", "temperature": "24.5" }
            """;

        JsonNode baseline = mapper.readTree(baselineJson);
        JsonNode incoming = mapper.readTree(coercedJson);

        Optional<DriftReport> result = detector.detectDrift("iot", "events.telemetry", baseline, incoming);

        assertTrue(result.isPresent());
        DriftReport report = result.get();
        assertEquals(DriftReport.DriftSeverity.MEDIUM_COERCIBLE, report.getSeverity());
        assertEquals(DriftReport.CompatibilityMode.FORWARD, report.getCompatibilityMode());
    }
}