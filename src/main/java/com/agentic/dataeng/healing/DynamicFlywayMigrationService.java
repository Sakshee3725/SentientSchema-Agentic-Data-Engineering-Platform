package com.agentic.dataeng.healing;

import com.agentic.dataeng.model.DriftReport;
import com.agentic.dataeng.model.DriftReport.FieldDelta;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * Enterprise Dynamic DDL Evolution Engine.
 * Formulates non-blocking PostgreSQL DDL (ADD COLUMN IF NOT EXISTS)
 * and applies it live without restarting the Spring Boot container.
 */
@Service
public class DynamicFlywayMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DynamicFlywayMigrationService.class);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public DynamicFlywayMigrationService(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    public String generateEvolutionDdl(DriftReport report) {
        String tableName = "pipeline_" + report.getPipelineId().toLowerCase() + "_events";
        StringBuilder sb = new StringBuilder();
        sb.append("-- Auto-generated Flyway DDL Patch by Self-Healing Agent\n");
        sb.append("BEGIN;\n");

        for (FieldDelta delta : report.getFieldDeltas()) {
            if (delta.getDeltaType() == DriftReport.DeltaType.FIELD_ADDED) {
                String col = delta.getFieldName();
                String type = delta.getRecommendedSqlType() != null ? delta.getRecommendedSqlType() : "VARCHAR(255)";
                sb.append("ALTER TABLE ").append(tableName)
                  .append(" ADD COLUMN IF NOT EXISTS ")
                  .append(col).append(" ").append(type)
                  .append(" DEFAULT NULL;\n");
            }
        }
        sb.append("COMMIT;\n");
        return sb.toString();
    }

    @Transactional
    public boolean applyDynamicMigration(String pipelineId, String ddlSql) {
        try {
            log.info(">>> Applying dynamic DDL patch for pipeline [{}]:\n{}", pipelineId, ddlSql);
            // Execute DDL dynamically against relational persistence layer
            jdbcTemplate.execute(ddlSql);
            
            // Record migration metadata in flyway_schema_history audit table
            String auditSql = "INSERT INTO flyway_schema_history " +
                    "(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) " +
                    "VALUES ((SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history), " +
                    "'" + System.currentTimeMillis() + "', 'self_healing_evolution', 'SQL', 'DYNAMIC_AGENT_DDL', 0, 'AGENTIC_SUPERVISOR', NOW(), 12, true)";
            
            try {
                jdbcTemplate.execute(auditSql);
            } catch (Exception ex) {
                log.warn("Flyway audit table record skipped (table may be managed in memory/test)");
            }

            log.info(">>> Dynamic DDL migration successfully executed. Pipeline schema evolved seamlessly!");
            return true;
        } catch (Exception e) {
            log.error("Failed to execute dynamic schema evolution: {}", e.getMessage(), e);
            return false;
        }
    }
}