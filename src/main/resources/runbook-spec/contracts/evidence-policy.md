# Evidence Policy 2.1

Evidence is required for APIs, business rules, database tables, Kafka consumers/producers, downstream dependencies, state transitions, retention rules, migration risks, configured alerts, and log signatures when present. Each evidence record needs a non-empty fact ID/type/fact, relative repository paths, valid positive line ranges, and HIGH, MEDIUM, or LOW confidence.

## Build Artifacts & Generated Files
Evidence citations must strictly refer to original authoritative source files (e.g. `src/main/...`, `src/test/...`, root build and configuration manifests). Generated build artifacts (`**/build/**`, `**/target/**`, `**/out/**`, `**/.gradle/**`, `**/node_modules/**`) are rejected by validators and must not appear in evidence.
