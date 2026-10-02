package com.agentic.dataeng;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Final Year Project Main Application:
 * Agentic Platform for Data Engineering and Self-Healing Schema Evolution.
 * 
 * Coordinates:
 * 1. Real-time Kafka Streaming Consumers
 * 2. AST-based Schema Drift Detection Engine
 * 3. Autonomous LangChain4j Agent Supervisor
 * 4. Dynamic Flyway DDL Evolution & In-Flight Type Adapters
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
@EnableScheduling
public class AgenticSchemaEvolutionApplication {

    private static final Logger log = LoggerFactory.getLogger(AgenticSchemaEvolutionApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AgenticSchemaEvolutionApplication.class, args);
    }

    @Bean
    public CommandLineRunner initBanner() {
        return args -> {
            log.info("==========================================================================");
            log.info("  AGENTIC DATA ENGINEERING & SELF-HEALING SCHEMA EVOLUTION PLATFORM");
            log.info("  Runtime: Java 21 | Spring Boot 3.3.3 | Apache Kafka | Flyway | LangChain4j");
            log.info("  Status: Active Pipeline Supervision Online. Ready for Stream Ingestion.");
            log.info("==========================================================================");
        };
    }
}