# Safety Policy 2.1

## Core Extractor Safety Constraints
The extractor is read-only. It produces only canonical facts, evidence, and restricted security-review candidates. It must not render, diff, publish, decide quality gates, alter repositories, expose secret values, or make external calls.

## Production Support Read-Only Rule
All support-facing fields (`supportCheck`, `supportAction`, `troubleshooting`, `unknownIncidentContext`, `escalationEvidence`, `supportGuidance`, `resolutionGuidance`) must provide **READ-ONLY inspection, verification, and evidence-gathering guidance only**.

### Permitted Support Actions
Production Support may only be instructed to:
- Inspect and query existing database records via read-only queries or APIs
- Inspect application, server, and access logs
- Inspect distributed traces and correlation IDs
- Inspect service metrics and health endpoints
- Inspect existing downstream-call evidence and attempt logs
- Inspect reconciliation status, runs, and captured evidence
- Inspect Kafka consumer group lag, offsets, and producer status
- Compare expected vs actual values
- Collect structured diagnostic evidence
- Escalate unresolved incidents with collected evidence

### Prohibited Support Instructions
Production Support must **NEVER** be instructed to:
- Initiate, trigger, run, execute, perform, force, or start reconciliation (manual or state-changing)
- Replay, reprocess, republish, or resend events, messages, or requests
- Manually retry or execute state-changing operations
- Change, alter, seek, or reset Kafka consumer offsets
- Update, delete, insert, drop, truncate, or alter production datastore records
- Force state transitions or mutate payment lifecycle states manually
- Force or manually trigger schedulers, cron jobs, or background workers
- Modify production configuration, environment variables, or secrets
- Restart or scale workloads, pods, containers, or services as a transaction-recovery action

### Distinguishing Application Behaviour vs Support Check
When the application or service performs automated recovery, background polling, or reconciliation:
- **Application Behaviour** (in `processingSummary`, `resilience`, `businessFlows`):
  * "Recovery worker automatically enquires downstream status and transitions pending payments."
- **Support Check** (in `supportCheck`, `guidance`, `unknownIncidentContext`):
  * "Inspect payment state, external call attempts and reconciliation evidence. Escalate if the outcome remains unresolved."

### UNKNOWN / DEEMED Outcomes Policy
For indeterminate, UNKNOWN, or DEEMED transaction outcomes:
- `supportCheck` must use evidence-oriented inspection wording:
  * "Inspect payment current_state, external_call_attempt records and existing reconciliation evidence. Escalate unresolved cases for controlled recovery."
- Never instruct Support to initiate enquiry or manual reconciliation.

### Examples of Safe vs Unsafe Support Checks

#### SAFE Examples (Read-Only & Prohibitive):
- `supportCheck`: "Inspect payment current_state, external_call_attempt records and existing reconciliation evidence. Escalate unresolved cases for controlled recovery."
- `supportCheck`: "Do not manually update database rows. Verify CBS account balance and logs."
- `supportCheck`: "Inspect payment version and payment_transition records."
- `guidance`: "Collect paymentId, stableRequestReference, and timeline via GET /api/v1/payments/{paymentId}/timeline. Do not manually update database rows or force schedulers."
- `guidance`: "Support must not initiate reconciliation. Never replay source Kafka events."

#### UNSAFE Examples (Prohibited & Rejected):
- ❌ `supportCheck`: "Check payment current_state and initiate enquiry or manual reconciliation" (Violates `NO_MANUAL_STATE_RECONCILIATION`)
- ❌ `supportCheck`: "Support should update the database record to SUCCESS" (Violates `NO_MANUAL_DATABASE_MUTATION`)
- ❌ `supportAction`: "L1/L2 can replay the Kafka event from topic" (Violates `NO_EVENT_REPLAY`)
- ❌ `guidance`: "Change the Kafka consumer offset to reprocess missed messages" (Violates `NO_OFFSET_MUTATION`)
- ❌ `supportCheck`: "Trigger manual reconciliation for this payment" (Violates `NO_MANUAL_STATE_RECONCILIATION`)
- ❌ `resolutionGuidance`: "Restart the service pod to recover stuck transactions" (Violates `NO_RESTART_SCALE_INSTRUCTION`)
