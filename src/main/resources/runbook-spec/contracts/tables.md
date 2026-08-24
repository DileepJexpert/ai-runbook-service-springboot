# Fixed Table Contract 2.1

The column order is invariant and values are escaped before Markdown or Confluence rendering.

| Section | Columns |
| --- | --- |
| Configuration Catalog | Component; Purpose; Property Key; Config Portal Key; Repository Value; Repository Default; Runtime Value Status; Sensitive; Support Lookup |
| Business Decision Rules | Business Condition; Data Used; Result / Response; Support Check |
| API / Event Contract | Endpoint/Event; Method/Direction; Authentication / Scope; Processing Model; Validation Summary; Success Meaning |
| Response & Error Mapping | Error Code / Log Signature; Result; Possible Causes; How to Confirm; Support Action |
| Downstream Dependencies | Dependency; Purpose; Timeout; Automatic Failure Handling; Failure Propagation; Support Check |
| Kafka | Topic; Direction; Consumer Group; Business Purpose; Failure Handling |
| Database | Table; Access; Lookup Key; Support-Relevant Fields; Operational Purpose |
| Retention | Data Store / Record; Retention / Expiry; Mechanism; Archive Destination; Support Impact |
| Migration | Migration; Change Type; Compatibility Risk; Support Meaning |
