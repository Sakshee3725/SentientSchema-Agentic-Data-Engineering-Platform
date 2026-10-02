package com.agentic.dataeng.healing;

import com.agentic.dataeng.model.DriftReport.FieldDelta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-Flight Adapter dynamically attached to Spring Kafka Deserializers.
 * Safely normalizes drifted types (e.g., String "49.99" -> Double 49.99)
 * so that existing JPA Entities and database writers receive conforming types.
 */
@Component
public class InFlightTypeCoercionFilter {

    private static final Logger log = LoggerFactory.getLogger(InFlightTypeCoercionFilter.class);
    private final Map<String, List<FieldDelta>> activeAdapters = new ConcurrentHashMap<>();

    public void registerPipelineFilter(String pipelineId, List<FieldDelta> deltas) {
        log.info(">>> Registering in-flight type adaptation filter for pipeline '{}' ({} fields)", pipelineId, deltas.size());
        activeAdapters.put(pipelineId, deltas);
    }

    public JsonNode adaptEvent(String pipelineId, JsonNode rawPayload) {
        List<FieldDelta> deltas = activeAdapters.get(pipelineId);
        if (deltas == null || deltas.isEmpty() || !rawPayload.isObject()) {
            return rawPayload; // No coercion needed
        }

        ObjectNode evolved = rawPayload.deepCopy();
        for (FieldDelta delta : deltas) {
            String field = delta.getFieldName();
            if (evolved.has(field)) {
                JsonNode val = evolved.get(field);
                
                // Example: Coerce String to Double
                if (val.isTextual() && "double".equalsIgnoreCase(delta.getSourceDataType())) {
                    try {
                        double parsed = Double.parseDouble(val.asText());
                        evolved.put(field, parsed);
                        log.debug("Coerced field '{}' string '{}' to double {}", field, val.asText(), parsed);
                    } catch (NumberFormatException ignored) {}
                }
                
                // Example: Coerce Integer to String
                if (val.isNumber() && "string".equalsIgnoreCase(delta.getSourceDataType())) {
                    evolved.put(field, val.asText());
                }
            }
        }
        return evolved;
    }
}