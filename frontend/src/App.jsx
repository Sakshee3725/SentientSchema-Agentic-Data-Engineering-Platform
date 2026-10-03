import { useEffect, useState } from "react";
import "./App.css";

function App() {
  const [health, setHealth] = useState(null);
  const [selectedPage, setSelectedPage] = useState("Overview");

  const [simulationResult, setSimulationResult] = useState(null);
  const [simulationLoading, setSimulationLoading] = useState(false);
  const [backendError, setBackendError] = useState(false);

  const menuItems = [
    { name: "Overview", icon: "⌂" },
    { name: "Agent Center", icon: "◉" },
    { name: "Drift Simulator", icon: "◇" },
    { name: "Schema Registry", icon: "▣" },
    { name: "Pipeline Explorer", icon: "⇄" },
    { name: "Self-Healing", icon: "⚡" },
    { name: "DLQ Monitor", icon: "⚠" },
    { name: "Schema History", icon: "◷" },
  ];

  useEffect(() => {
    fetch("/api/v1/schema-evolution/health")
      .then((response) => {
        if (!response.ok) {
          throw new Error("Backend unavailable");
        }
        return response.json();
      })
      .then((data) => {
        setHealth(data);
        setBackendError(false);
      })
      .catch((error) => {
        console.error("Backend connection failed:", error);
        setBackendError(true);
      });
  }, []);

  const runSimulation = async () => {
    setSimulationLoading(true);
    setSimulationResult(null);

    const requestBody = {
      pipelineId: "ecommerce_orders",
      baselineSchema: {
        order_id: "string",
        customer_id: "integer",
        amount: "double",
        status: "string",
      },
      driftedPayload: {
        order_id: "ORD-101",
        customer_id: 55,
        amount: 1500.50,
        status: "CONFIRMED",
        discount_code: "AUTUMN20",
      },
    };

    try {
      const response = await fetch(
        "/api/v1/schema-evolution/simulate-drift",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(requestBody),
        }
      );

      const data = await response.json();

      if (!response.ok) {
        throw new Error(data.message || "Simulation failed");
      }

      setSimulationResult(data);
    } catch (error) {
      console.error("Simulation failed:", error);
      setSimulationResult({
        status: "ERROR",
        message: error.message,
      });
    } finally {
      setSimulationLoading(false);
    }
  };

  const pipelines = [
    {
      name: "ecommerce_orders",
      topic: "events.ecommerce_orders",
      status: "Healthy",
    },
    {
      name: "fintech_cdc",
      topic: "events.fintech_cdc",
      status: "Healthy",
    },
    {
      name: "iot_telemetry",
      topic: "events.iot_telemetry",
      status: "Healthy",
    },
    {
      name: "healthcare_ehr",
      topic: "events.healthcare_ehr",
      status: "Healthy",
    },
  ];

  const renderDriftSimulator = () => {
    const report = simulationResult?.driftReport;
    const healing = simulationResult?.healingPlan;

    return (
      <>
        <section className="simulator-hero">
          <div>
            <div className="eyebrow">AUTONOMOUS SCHEMA ANALYSIS</div>
            <h2>Live Drift Simulator</h2>
            <p>
              Inject a schema change and watch the autonomous
              data-engineering agents detect, reason and respond.
            </p>
          </div>
          <div className="pipeline-badge">
            <span></span>
            ecommerce_orders
          </div>
        </section>

        <div className="drift-flow">
          <section className="schema-card">
            <div className="card-top">
              <div>
                <span className="step-number">01</span>
                <h3>Baseline Schema</h3>
              </div>
              <span className="status-pill neutral">
                VERSION 1
              </span>
            </div>
            <p className="card-description">
              Registered schema expected by the pipeline.
            </p>
            <pre className="json-editor">
{`{
  "order_id": "string",
  "customer_id": "integer",
  "amount": "double",
  "status": "string"
}`}
            </pre>
          </section>

          <div className="flow-arrow">→</div>

          <section className="schema-card incoming-card">
            <div className="card-top">
              <div>
                <span className="step-number">02</span>
                <h3>Incoming Event</h3>
              </div>
              <span className="status-pill warning">
                NEW EVENT
              </span>
            </div>
            <p className="card-description">
              Event received from the Kafka stream.
            </p>
            <pre className="json-editor">
{`{
  "order_id": "ORD-101",
  "customer_id": 55,
  "amount": 1500.50,
  "status": "CONFIRMED",
  "discount_code": "AUTUMN20"
}`}
            </pre>
          </section>
        </div>

        <section className="simulation-action">
          <div>
            <span className="live-dot"></span>
            <div>
              <strong>Agent Supervisor Ready</strong>
              <p>
                Schema detector is waiting for an event.
              </p>
            </div>
          </div>
          <button
            className="simulate-btn"
            onClick={runSimulation}
            disabled={simulationLoading}
          >
            {simulationLoading
              ? "Agents Analyzing..."
              : "⚡ Detect & Heal Drift"}
          </button>
        </section>

        {simulationResult && simulationResult.status !== "ERROR" && (
          <>
            <section className="analysis-section">
              <div className="section-heading">
                <div>
                  <span className="eyebrow">DETECTION ENGINE</span>
                  <h2>Detected Changes</h2>
                </div>
                <span className="severity-badge">
                  {report?.severity || "UNKNOWN"}
                </span>
              </div>
              <div className="changes-grid">
                <div className="change-summary">
                  <strong>
                    {report?.fieldDeltas?.length || 0}
                  </strong>
                  <span>schema changes</span>
                </div>
                {report?.fieldDeltas?.map((delta, index) => (
                  <div className="change-card" key={index}>
                    <div className="change-icon">
                      +
                    </div>
                    <div>
                      <h3>{delta.fieldName}</h3>
                      <p>
                        {delta.deltaType}
                      </p>
                      <small>
                        {delta.explanation ||
                          "Schema difference detected by the drift engine."}
                      </small>
                    </div>
                  </div>
                ))}
              </div>
            </section>

            <section className="analysis-section">
              <div className="section-heading">
                <div>
                  <span className="eyebrow">
                    AGENT INTELLIGENCE
                  </span>
                  <h2>AI Reasoning</h2>
                </div>
                <span className="reasoning-status">
                  ANALYSIS COMPLETE
                </span>
              </div>
              <div className="reasoning-console">
                <div className="reasoning-line">
                  <span className="agent-time">01</span>
                  <div className="agent-node observe">
                    ◉
                  </div>
                  <div>
                    <strong>Observe Agent</strong>
                    <p>
                      Incoming event structure captured from
                      ecommerce_orders.
                    </p>
                  </div>
                </div>
                <div className="reasoning-line">
                  <span className="agent-time">02</span>
                  <div className="agent-node reason">
                    ◇
                  </div>
                  <div>
                    <strong>Reasoning Agent</strong>
                    <p>
                      Comparing incoming fields against the
                      registered baseline schema.
                    </p>
                  </div>
                </div>
                <div className="reasoning-line">
                  <span className="agent-time">03</span>
                  <div className="agent-node decide">
                    ◆
                  </div>
                  <div>
                    <strong>Decision Agent</strong>
                    <p>
                      Compatibility mode:
                      {" "}
                      <b>
                        {report?.compatibilityMode ||
                          "UNKNOWN"}
                      </b>
                    </p>
                  </div>
                </div>
                <div className="reasoning-line">
                  <span className="agent-time">04</span>
                  <div className="agent-node execute">
                    ⚡
                  </div>
                  <div>
                    <strong>Execution Agent</strong>
                    <p>
                      Selected healing strategy:
                      {" "}
                      <b>
                        {healing?.strategy ||
                          "PENDING"}
                      </b>
                    </p>
                  </div>
                </div>
              </div>
            </section>

            <section className="healing-decision">
              <div className="decision-header">
                <div>
                  <span className="eyebrow">
                    AUTONOMOUS RESPONSE
                  </span>
                  <h2>Healing Decision</h2>
                </div>
                <div className="execution-success">
                  <span>✓</span>
                  {healing?.executedSuccessfully
                    ? "EXECUTED SUCCESSFULLY"
                    : "REQUIRES ATTENTION"}
                </div>
              </div>
              <div className="decision-grid">
                <div>
                  <span>SELECTED STRATEGY</span>
                  <strong>
                    {healing?.strategy ||
                      "Not available"}
                  </strong>
                </div>
                <div>
                  <span>COMPATIBILITY</span>
                  <strong>
                    {report?.compatibilityMode ||
                      "Not available"}
                  </strong>
                </div>
                <div>
                  <span>HUMAN REVIEW</span>
                  <strong>
                    {report?.requiresHumanReview
                      ? "REQUIRED"
                      : "NOT REQUIRED"}
                  </strong>
                </div>
              </div>
              {healing?.generatedSqlDdl && (
                <div className="sql-block">
                  <div className="sql-header">
                    <span>GENERATED SQL / DDL</span>
                    <span>EXECUTION PLAN</span>
                  </div>
                  <pre>
                    {healing.generatedSqlDdl}
                  </pre>
                </div>
              )}
            </section>
          </>
        )}

        {simulationResult?.status === "ERROR" && (
          <section className="error-panel">
            <h3>Simulation Failed</h3>
            <p>
              {simulationResult.message}
            </p>
          </section>
        )}
      </>
    );
  };

  const renderPage = () => {
    if (selectedPage === "Drift Simulator") {
      return renderDriftSimulator();
    }

    if (selectedPage === "Agent Center") {
      return (
        <>
          <section className="panel">
            <h2>Agent Intelligence Center</h2>
            <div className="healing-flow">
              <div>
                <b>1</b>
                <p>Observe</p>
              </div>
              <div className="arrow">→</div>
              <div>
                <b>2</b>
                <p>Reason</p>
              </div>
              <div className="arrow">→</div>
              <div>
                <b>3</b>
                <p>Decide</p>
              </div>
              <div className="arrow">→</div>
              <div>
                <b>4</b>
                <p>Execute</p>
              </div>
              <div className="arrow">→</div>
              <div>
                <b>5</b>
                <p>Verify</p>
              </div>
            </div>
          </section>
          <section className="panel">
            <h2>Agent Status</h2>
            <div className="pipeline">
              <div>
                <h3>Schema Drift Detector</h3>
                <p>Monitoring incoming event schemas</p>
              </div>
              <span className="healthy">ACTIVE</span>
            </div>
            <div className="pipeline">
              <div>
                <h3>Reasoning Agent</h3>
                <p>Analyzing schema compatibility</p>
              </div>
              <span className="healthy">ACTIVE</span>
            </div>
            <div className="pipeline">
              <div>
                <h3>Self-Healing Executor</h3>
                <p>Executing approved schema changes</p>
              </div>
              <span className="healthy">ACTIVE</span>
            </div>
          </section>
        </>
      );
    }

    if (selectedPage === "Schema Registry") {
      return (
        <section className="panel">
          <h2>Schema Registry</h2>
          {pipelines.map((pipeline) => (
            <div className="pipeline" key={pipeline.name}>
              <div>
                <h3>{pipeline.name}</h3>
                <p>{pipeline.topic}</p>
              </div>
              <span className="healthy">
                REGISTERED
              </span>
            </div>
          ))}
        </section>
      );
    }

    if (selectedPage === "Pipeline Explorer") {
      return (
        <section className="panel">
          <h2>Pipeline Explorer</h2>
          {pipelines.map((pipeline) => (
            <div className="pipeline" key={pipeline.name}>
              <div>
                <h3>{pipeline.name}</h3>
                <p>
                  Kafka Topic: {pipeline.topic}
                </p>
              </div>
              <span className="healthy">
                {pipeline.status}
              </span>
            </div>
          ))}
        </section>
      );
    }

    if (selectedPage === "Self-Healing") {
      return (
        <>
          <section className="panel">
            <h2>Autonomous Self-Healing</h2>
            <div className="healing-flow">
              <div>
                <b>1</b>
                <p>Detect</p>
              </div>
              <div className="arrow">→</div>
              <div>
                <b>2</b>
                <p>Analyze</p>
              </div>
              <div className="arrow">→</div>
              <div>
                <b>3</b>
                <p>Generate</p>
              </div>
              <div className="arrow">→</div>
              <div>
                <b>4</b>
                <p>Execute</p>
              </div>
              <div className="arrow">→</div>
              <div>
                <b>5</b>
                <p>Verify</p>
              </div>
            </div>
          </section>
          <section className="panel">
            <h2>Current Agent State</h2>
            <div className="activity">
              <h3>Autonomous Supervisor Active</h3>
              <p>
                Monitoring all Kafka pipelines for schema
                changes and compatibility violations.
              </p>
            </div>
          </section>
        </>
      );
    }

    if (selectedPage === "DLQ Monitor") {
      return (
        <section className="panel">
          <h2>Dead Letter Queue / Quarantine</h2>
          <div className="activity">
            <h3>No quarantined events</h3>
            <p>
              All monitored events are currently processing
              successfully.
            </p>
          </div>
        </section>
      );
    }

    if (selectedPage === "Schema History") {
      return (
        <section className="panel">
          <h2>Schema Evolution History</h2>
          <div className="pipeline">
            <div>
              <h3>ecommerce_orders</h3>
              <p>
                Schema v1 → v2 • Additive field detected
              </p>
            </div>
            <span className="healthy">
              AUTO-HEALED
            </span>
          </div>
          <div className="pipeline">
            <div>
              <h3>fintech_cdc</h3>
              <p>
                Schema v3 → v3 • No drift detected
              </p>
            </div>
            <span className="healthy">
              STABLE
            </span>
          </div>
          <div className="pipeline">
            <div>
              <h3>iot_telemetry</h3>
              <p>
                Schema v2 → v2 • Type compatibility verified
              </p>
            </div>
            <span className="healthy">
              VERIFIED
            </span>
          </div>
        </section>
      );
    }

    return (
      <>
        <section className="stats">
          <div className="card">
            <h3>System Status</h3>
            <strong>
              {health?.status || "Loading..."}
            </strong>
            <p>All services operational</p>
          </div>
          <div className="card">
            <h3>Active Pipelines</h3>
            <strong>
              {health?.activePipelines || "Loading..."}
            </strong>
            <p>Kafka pipelines running</p>
          </div>
          <div className="card">
            <h3>Kafka Cluster</h3>
            <strong>
              {health?.kafkaCluster || "Loading..."}
            </strong>
            <p>Kafka topics monitored</p>
          </div>
          <div className="card">
            <h3>Self-Healing Agent</h3>
            <strong>
              {health?.supervisor
                ? "Active"
                : "Loading..."}
            </strong>
            <p>
              {health?.supervisor ||
                "Connecting to backend..."}
            </p>
          </div>
        </section>

        <section className="panel">
          <h2>Pipeline Monitoring</h2>
          {pipelines.map((pipeline) => (
            <div
              className="pipeline"
              key={pipeline.name}
            >
              <div>
                <h3>{pipeline.name}</h3>
                <p>{pipeline.topic}</p>
              </div>
              <span className="healthy">
                {pipeline.status}
              </span>
            </div>
          ))}
        </section>

        <section className="panel">
          <h2>Autonomous Agent Activity</h2>
          <div className="healing-flow">
            <div>
              <b>1</b>
              <p>Observe</p>
            </div>
            <div className="arrow">→</div>
            <div>
              <b>2</b>
              <p>Reason</p>
            </div>
            <div className="arrow">→</div>
            <div>
              <b>3</b>
              <p>Decide</p>
            </div>
            <div className="arrow">→</div>
            <div>
              <b>4</b>
              <p>Execute</p>
            </div>
            <div className="arrow">→</div>
            <div>
              <b>5</b>
              <p>Verify</p>
            </div>
          </div>
          <div className="activity">
            <h3>
              {backendError
                ? "Backend connection unavailable"
                : "Autonomous supervisor is monitoring"}
            </h3>
            <p>
              The platform continuously observes Kafka
              pipelines for schema drift and compatibility
              violations.
            </p>
          </div>
        </section>
      </>
    );
  };

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="logo">
          <h2>Agentic Platform</h2>
          <p>
            Autonomous Data Engineering
          </p>
        </div>
        <nav>
          {menuItems.map((item) => (
            <a
              key={item.name}
              className={
                selectedPage === item.name
                  ? "active"
                  : ""
              }
              onClick={() =>
                setSelectedPage(item.name)
              }
            >
              <span
                style={{
                  marginRight: "10px",
                }}
              >
                {item.icon}
              </span>
              {item.name}
            </a>
          ))}
        </nav>
      </aside>

      <main className="main">
        <header>
          <div>
            <h1>{selectedPage}</h1>
            <p>
              Autonomous Data Engineering &
              Self-Healing Schema Evolution
            </p>
          </div>
          <div className="system-status">
            <span></span>
            {health?.status === "UP"
              ? "System Online"
              : "Connecting..."}
          </div>
        </header>
        {renderPage()}
      </main>
    </div>
  );
}

export default App;
