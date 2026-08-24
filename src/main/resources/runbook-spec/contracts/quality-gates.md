# Quality Gates 2.1

Deterministic outcomes are PASS, PASS_WITH_WARNINGS, RENDER_ALLOWED_PUBLISH_BLOCKED, and FAILED.

FAILED codes include SCHEMA_INVALID, EVIDENCE_INVALID, SAFETY_POLICY_VIOLATION, SECRET_VALUE_DETECTED, UNSUPPORTED_CONTRACT_VERSION, and MALFORMED_AI_OUTPUT. PARTIAL_SCAN blocks publishing but can produce RENDER_ALLOWED_PUBLISH_BLOCKED when useful validated facts exist. Gates are shape-aware: only observed REST, Kafka, or state-changing integration shapes create their respective critical-fact requirements.
