# Standard Platform Context 2.1

The current service repository is authoritative for service-specific behavior.

Typical platform capabilities may include Java/Spring Boot, Kubernetes/EKS, Kafka/MSK,
PostgreSQL/JPA, Aerospike, ELK/Kibana, Prometheus/Grafana, distributed tracing, and
centralized configuration. Do not assume a capability is used without repository evidence.

## Production Support Boundaries
Generated runbook guidance must be strictly read-only. The generated runbook must NEVER instruct Support to:
- Initiate or trigger reconciliation (manual or state-changing)
- Replay, reprocess, republish, or resend events, messages, or requests
- Alter, change, or reset Kafka consumer offsets
- Mutate, update, delete, or insert production database records
- Force state transitions or force background schedulers/workers
- Modify production configuration, environment variables, or secrets
- Restart or scale workloads/services as a transaction recovery action

ServiceNow is authoritative for incident and RCA history.
