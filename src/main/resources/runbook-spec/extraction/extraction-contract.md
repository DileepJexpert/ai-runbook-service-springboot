# Service Intelligence Extraction Contract 2.1

## 1. Core Principles and Repository Authority
- **Current Checked-Out Repository is Authoritative**: Extraction is strictly based on the current checked-out Git repository commit.
- **No Historical Runbook Reliance**: Previous runbook JSON files or published Confluence documents are never current service truth.
- **Generated Build Directories Excluded**: The extractor must strictly ignore generated/transient build output directories including `**/build/**`, `**/target/**`, `**/out/**`, `**/.gradle/**`, and `**/node_modules/**`. Source citations must point to authoritative files (e.g. `src/main/...`, `src/test/...`, `db/migration/...`, deployment manifests, configuration files).
- **Scan Completeness vs Fact Confidence**: `scanStatus` must be set to `COMPLETE` only when all relevant operational areas of the repository were inspected; otherwise set `PARTIAL` and list all unscanned areas in `unscannedAreas`. Scan completeness is independent of individual fact confidence (`HIGH`, `MEDIUM`, `LOW`).

## 2. AI Artifact Ownership
For contract 2.1, the extractor is strictly responsible for generating **exactly three AI-owned JSON artifacts** into the requested output directory:
1. `runbook-data.json` (canonical structured Service Intelligence facts)
2. `runbook-evidence.json` (provenance, confidence, and exact file/line source citations)
3. `security-findings.json` (candidate security findings; must ALWAYS be created, containing `{"contractVersion": "2.1", "findings": []}` when zero findings are detected)

The AI extractor must **NOT**:
- Modify the target repository or any files outside the assigned output directory.
- Render Markdown (`RUNBOOK.md`) or Confluence HTML (`confluence-body.html`).
- Normalize JSON or assign final deterministic stable IDs.
- Compute semantic operational diffs (`operational-diff.json`, `runbook-delta.json`).
- Write generation reports (`generation-report.json`).
- Evaluate quality gates or make automated publishing decisions.
- Perform live external network calls or disclose raw secret values.

## 3. Fact Extraction and Evidence Rules

### 3.1. Evidence Requirements for Material Facts
- Every material fact in `runbook-data.json` must have a corresponding entry in `runbook-evidence.json` with `factId`, `factType`, `fact`, `provenance`, `confidence`, and `sourceEvidence` (file path, `lineStart`, `lineEnd`).
- `regulatoryBasis` must **NEVER** be hallucinated or inferred; set to `NOT_ESTABLISHED` unless an explicit statutory/regulatory citation exists in code or metadata.

### 3.2. Configuration Extraction and Scoping
- **Multi-Module Component Scope**: In multi-module monorepos, configuration properties must retain component scope (`componentId`, `componentName`, `modulePath`, `deploymentUnit`). A property key (e.g. `server.port`) and external key (e.g. `SERVER_PORT`) must not be collapsed across distinct deployable subprojects.
- **Stable ID Identity**: Formatted as `CONFIG:<COMPONENT_ID>:<PROPERTY_KEY>` (e.g. `CONFIG:apps/pravah-app:spring.datasource.url`).
- **Property vs External Config Key**: Distinguish internal application property key (`propertyKey`, e.g. `spring.datasource.url`) from external environment/Config Portal key (`configKey`, e.g. `PRAVAH_DB_URL`).
- **Repository Value vs Default**:
  - `repositoryValue`: Direct static value defined in repository files without placeholders.
  - `repositoryDefault`: Fallback value inside placeholder expressions `${CONFIG_KEY:default_value}`.
- **Runtime Value Status**:
  - `KNOWN_FROM_REPOSITORY`: Value is fully statically declared in repository.
  - `REPOSITORY_DEFAULT`: Default is known, but external environment overrides are typical.
  - `CHECK_CONFIG_PORTAL`: Value is expected from an external Config Portal or environment variable.
  - `PROTECTED_CHECK_CONFIG_PORTAL`: Credential/secret value; marked `sensitive=true`. Never store or emit raw secret text.
  - `NOT_FOUND_IN_REPOSITORY`: Property referenced but no value or default is present.
  - `NOT_APPLICABLE`: Property not applicable in this deployment context.

### 3.3. APIs and Validation
- Extract HTTP REST endpoints, methods, paths, processing models (synchronous, asynchronous, batch), validation rules, error mappings, and success meanings.
- Maintain case-sensitive paths, header names (`Idempotency-Key`), query parameters, and payload schemas.

### 3.4. Business Flows and Decision Rules
- Map end-to-end business flows from trigger to terminal outcome.
- Extract decision rules (`businessRules`) with explicit condition, data source, and result.

### 3.5. Database, Persistence, and State Management
- Extract database tables (`DB_TABLE:<schema>:<table>`), access modes (`READ`, `READ_WRITE`), lookup keys, support-relevant fields, and operational purposes from JPA entities and Flyway SQL migrations.
- State transitions require direct implementation transition evidence (state machines, database transition tables, event listeners).
- Financial transactions and control ledgers require actual write/transaction evidence (e.g. double-entry debit/credit ledger records, causal aggregate versioning).
- Concurrency and locking: Record optimistic locking (`@Version`), row locking (`FOR UPDATE SKIP LOCKED`), and distributed lease durations.

### 3.6. Messaging and Event Streaming (Kafka)
- Extract Kafka consumers (`KAFKA_CONSUMER:<topic>:<group>`) and producers (`KAFKA_PRODUCER:<topic>:<purpose>`).
- Preserve case-sensitive topic names, consumer group IDs, message keying strategies (e.g. aggregate UUID partition keying), causal ordering constraints, and inbox deduplication mechanisms (`consumer_inbox`).

### 3.7. Downstream Dependencies and Response Interpretation
- Extract all downstream integrations (CBS, Payment Rails, external APIs, databases, brokers).
- Map canonical fields:
  - `name`: Downstream system identifier.
  - `purpose`: Functional role.
  - `timeout`: Configured network timeout.
  - `automaticFailureHandling`: How the service automatically handles downstream timeouts, network failures, or business declines (e.g. marking states DEEMED, executing automated reversals).
  - `failurePropagation`: How the failure is exposed to the upstream caller.
  - `supportCheck`: Read-only verification steps for support engineers.

### 3.8. Idempotency, Resilience, and Reconciliation
- `idempotency`: Header-based idempotency constraints, request hashing (SHA-256 payload canonicalization), unique DB constraints, and deterministic external reference generation.
- `resilience`: Retry policies, backoff intervals, circuit breakers, recovery worker schedulers.
- `reconciliation`: Distributed lease claiming (`FOR UPDATE SKIP LOCKED`), matching algorithms, and clearing file ingestion.
- `retentionRules`: Datastore retention periods and archival mechanisms.

### 3.9. Observability, Health, and Security
- `logSignatures`: Specific error logs, failure codes, and operational interpretations.
- `traceIdentifiers`: Correlation IDs (`paymentId`, `clientReference`, `stableRequestReference`, `cycleReference`).
- `supportHealthChecks`: Spring Boot Actuator endpoints (`/actuator/health`, `/actuator/prometheus`), Kubernetes liveness/readiness probes.
- `security-findings.json`: Restricted candidate findings for hardcoded default credentials, unauthenticated financial endpoints, sensitive logging, or weak TLS configurations.

## 4. Production Support Read-Only Rule

All support-facing fields (`supportCheck`, `supportAction`, `troubleshooting`, `unknownIncidentContext`, `escalationEvidence`, `supportGuidance`, `resolutionGuidance`) must provide **READ-ONLY inspection, verification, and evidence-gathering guidance only**.

### 4.1. Permitted Support Actions
Production Support may only be instructed to:
- Inspect and query existing database records via read-only queries or APIs.
- Inspect application, server, and access logs.
- Inspect distributed traces and correlation IDs.
- Inspect service metrics and health endpoints.
- Inspect existing downstream-call evidence and attempt logs.
- Inspect reconciliation status, runs, and captured evidence.
- Inspect Kafka consumer group lag, offsets, and producer status.
- Compare expected vs actual values.
- Collect structured diagnostic evidence and escalate unresolved incidents.

### 4.2. Prohibited Support Instructions
Production Support must **NEVER** be instructed to:
- Initiate, trigger, run, execute, perform, force, or start reconciliation (manual or state-changing).
- Replay, reprocess, republish, or resend events, messages, or requests.
- Manually retry or execute state-changing operations.
- Change, alter, seek, or reset Kafka consumer offsets.
- Update, delete, insert, drop, truncate, or alter production datastore records.
- Force state transitions or mutate payment lifecycle states manually.
- Force or manually trigger schedulers, cron jobs, or background workers.
- Modify production configuration, environment variables, or secrets.
- Restart or scale workloads, pods, containers, or services as a transaction-recovery action.

### 4.3. Distinguishing Application Behaviour vs Support Check
When the application performs automated recovery, background polling, or reconciliation:
- **Application Behaviour** (in `processingSummary`, `resilience`, `businessFlows`):
  * "Recovery worker automatically enquires downstream status and transitions pending payments."
- **Support Check** (in `supportCheck`, `guidance`, `unknownIncidentContext`):
  * "Inspect payment state, external call attempts and reconciliation evidence. Escalate if the outcome remains unresolved."

### 4.4. UNKNOWN / DEEMED Outcomes Policy
For indeterminate, UNKNOWN, or DEEMED transaction outcomes:
- `supportCheck` must use evidence-oriented inspection wording:
  * "Inspect payment current_state, external_call_attempt records and existing reconciliation evidence. Escalate unresolved cases for controlled recovery."
- Never instruct Support to initiate enquiry or manual reconciliation.

### 4.5. Examples of Safe vs Unsafe Support Checks

#### SAFE Examples (Read-Only & Prohibitive):
- `supportCheck`: "Inspect payment current_state, external_call_attempt records and existing reconciliation evidence. Escalate unresolved cases for controlled recovery."
- `supportCheck`: "Do not manually update database rows. Verify CBS account balance and logs."
- `supportCheck`: "Inspect payment version and payment_transition records."
- `guidance`: "Collect paymentId, stableRequestReference, and timeline via GET /api/v1/payments/{paymentId}/timeline. Do not manually update database rows or force schedulers."
- `guidance`: "Support must not initiate reconciliation. Never replay source Kafka events."

#### UNSAFE Examples (Prohibited & Rejected by Pipeline Safety Gate):
- ❌ `supportCheck`: "Check payment current_state and initiate enquiry or manual reconciliation" (Violates `NO_MANUAL_STATE_RECONCILIATION`)
- ❌ `supportCheck`: "Support should update the database record to SUCCESS" (Violates `NO_MANUAL_DATABASE_MUTATION`)
- ❌ `supportAction`: "L1/L2 can replay the Kafka event from topic" (Violates `NO_EVENT_REPLAY`)
- ❌ `guidance`: "Change the Kafka consumer offset to reprocess missed messages" (Violates `NO_OFFSET_MUTATION`)
- ❌ `supportCheck`: "Trigger manual reconciliation for this payment" (Violates `NO_MANUAL_STATE_RECONCILIATION`)
- ❌ `resolutionGuidance`: "Restart the service pod to recover stuck transactions" (Violates `NO_RESTART_SCALE_INSTRUCTION`)
