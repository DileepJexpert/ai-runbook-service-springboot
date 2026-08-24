# Runbook Presentation Contract 2.0

Both implementations render these headings in this exact order:

1. Service Overview & Criticality
2. Quick Support Summary
3. Business Flow & Decision Guide
4. API / Event Contract & Validation
5. Business Decision Rules
6. Response & Error Mapping
7. Data Origin, Reference Data & Transformation
8. Transaction Lifecycle, States & Recovery
9. Downstream Dependencies & Response Interpretation
10. Datastore Support Evidence
11. Kafka & Asynchronous Processing
12. Resilience & Automatic Failure Handling
13. Rate Limiting, Capacity & Concurrency
14. Data Retention, Expiry & Archival
15. Idempotency & Duplicate Protection
16. Deployment / Schema Compatibility
17. Operational Errors & Troubleshooting
18. Alerts / Support Health Checks & Monitoring
19. Transaction Tracing
20. Unknown / Unclassified Incident Triage
21. Support Responsibility & Access
22. Escalation Evidence Checklist
23. Pipeline & Generation Metadata

Fixed table columns:

- Business rules: Business Condition, Data Used, Result / Response, Support Check.
- API/event: Endpoint/Event, Method/Direction, Authentication / Scope, Processing Model, Validation Summary, Success Meaning.
- Errors: Error Code / Log Signature, Result, Possible Causes, How to Confirm, Support Action.
- Downstreams: Dependency, Purpose, Timeout, Automatic Failure Handling, Failure Propagation, Support Check.
- Kafka: Topic, Direction, Consumer Group, Business Purpose, Failure Handling.
- Database: Table, Access, Lookup Key, Support-Relevant Fields, Operational Purpose.
- Retention: Data Store / Record, Retention / Expiry, Mechanism, Archive Destination, Support Impact.
- Migration: Migration, Change Type, Compatibility Risk, Support Meaning.
