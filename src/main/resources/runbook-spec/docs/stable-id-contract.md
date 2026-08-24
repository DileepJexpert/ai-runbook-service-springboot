# Stable ID Contract 2.1

Natural IDs are `API:<METHOD>:<PATH>`, `KAFKA_CONSUMER:<TOPIC>:<GROUP>`, `KAFKA_PRODUCER:<TOPIC>`, `DB_TABLE:<SCHEMA>:<TABLE>`, `DEPENDENCY:<NORMALIZED_NAME>`, and `FEATURE_FLAG:<PROPERTY>`.

Configuration IDs are deterministic and scope-aware:
- Scoped component configuration: `CONFIG:<COMPONENT_ID>:<PROPERTY_KEY>` (e.g. `CONFIG:edges/sfdc-ingress-edge:server.port`, `CONFIG:capabilities/bureau:server.port`)
- Root / global configuration: `CONFIG:GLOBAL:<PROPERTY_KEY>` or `CONFIG:<PROPERTY_KEY>`

A business-rule ID is `RULE:` plus the first 20 hexadecimal characters of SHA-256 over flow ID, source file, containing method, and whitespace-normalized lowercase condition joined with `|`.
