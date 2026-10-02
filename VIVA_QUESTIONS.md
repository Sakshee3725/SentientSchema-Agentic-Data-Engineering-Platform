# Final Year Project Defense: Viva-Voce Q&A Cheat Sheet

### Q1: What is schema drift, and why is it considered one of the hardest challenges in modern Data Engineering?
**Examiner Focus**: *Assesses theoretical foundation and problem motivation.*

**Authoritative Answer**:
Schema drift occurs when upstream data producers (microservices, 3rd party APIs, IoT sensors) modify their payload structure without coordinating with downstream consumers. In decoupled distributed systems like Kafka, downstream ETL/ELT pipelines make rigid assumptions regarding field names and data types. A single unannounced type change (e.g., integer to string) or dropped column causes Jackson/Avro deserialization exceptions, halting consumer group offsets and blocking subsequent transactions (poison pill effect).

**Source Code Reference**: `SchemaDriftDetector.java`

---

### Q2: How does your AST Tree-Diffing algorithm compare with Confluent Schema Registry's native compatibility checks?
**Examiner Focus**: *Deep dive into algorithmic contribution and schema registry internals.*

**Authoritative Answer**:
Confluent Schema Registry validates Avro/JSON schemas strictly at the registry level before message serialization. However, real-world distributed architectures often ingest schemaless JSON, REST payloads, or CDC event streams where producers bypass the registry. Our Java AST Tree-Diffing engine operates at the consumer boundary using Jackson's hierarchical JsonNode representations. It detects not only syntactic additions/deletions, but also evaluates semantic renames and mathematical coercibility, dynamically registering evolved versions with the Schema Registry post-healing.

**Source Code Reference**: `SchemaDriftDetector.java:55`

---

### Q3: How does the platform ensure zero-downtime database evolution without locking PostgreSQL tables?
**Examiner Focus**: *Tests systems engineering, database internals, and concurrency control.*

**Authoritative Answer**:
When additive schema drift is detected, our DynamicFlywayMigrationService generates non-blocking DDL: 'ALTER TABLE table_name ADD COLUMN IF NOT EXISTS column_name DATA_TYPE DEFAULT NULL;'. In PostgreSQL 11+, adding a column with a NULL default takes only an ACCESS EXCLUSIVE lock for a few milliseconds to update the catalog (pg_attribute), without rewriting the physical table pages. The migration is applied programmatically via JdbcTemplate inside an isolated transaction and cataloged in the flyway_schema_history table.

**Source Code Reference**: `DynamicFlywayMigrationService.java:38`

---

### Q4: What is the ReAct agentic pattern, and why use an Agent instead of simple if-else rule heuristics?
**Examiner Focus**: *Evaluates AI agent integration and architectural justification.*

**Authoritative Answer**:
The ReAct (Reason + Act) loop allows the supervisor agent to perform multi-step reasoning: Observation (analyzing the AST delta) -> Deliberation (evaluating business risk, compatibility mode, and downstream dependencies) -> Action (invoking tool functions like Flyway DDL generator or DLQ routing) -> Verification. While deterministic rules handle trivial additive fields, an Agent is necessary to handle complex ambiguities—such as semantic field renames (e.g., identifying that 'customer_id' was renamed to 'customer_uuid' based on context) or synthesizing custom regex transformations.

**Source Code Reference**: `SelfHealingAgentService.java:42`

---

### Q5: What happens when a breaking change cannot be auto-healed? Explain your DLQ strategy.
**Examiner Focus**: *Tests fault tolerance, reliability, and edge case handling.*

**Authoritative Answer**:
If a mandatory column with a database NOT NULL constraint is missing or an unparseable binary corruption occurs, automated DDL would corrupt data integrity. In this scenario, the agent classifies the drift as HIGH_BREAKING. The event is wrapped in an audited DLQ envelope with metadata (failure reason, timestamp, pipeline ID, original payload) and routed to a dedicated Kafka topic (e.g., 'events.orders.DLQ'). This isolates the poison-pill record, allowing the main consumer group to continue processing without downtime.

**Source Code Reference**: `DeadLetterQueueQuarantineService.java:30`

---

### Q6: How did you implement in-flight type coercion without restarting Spring Boot worker threads?
**Examiner Focus**: *Assesses Java concurrency, memory models, and Spring Kafka internals.*

**Authoritative Answer**:
We created an InFlightTypeCoercionFilter utilizing thread-safe ConcurrentHashMaps to hold active dynamic transformations. When the agent detects coercible mutations (e.g., numeric string '149.95' to Double), it registers an adapter rule in real time. The Spring Kafka consumer executes this filter inside its deserializer chain before the event reaches the JPA Entity / JDBC persistence layer. This ensures existing database schemas receive expected primitive types without needing a JVM reboot or pod redeployment.

**Source Code Reference**: `InFlightTypeCoercionFilter.java:24`

---
