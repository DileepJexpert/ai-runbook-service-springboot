# IDFC AI Runbook / Service Intelligence
## Agreed End-to-End Architecture and Processing Flow

**Purpose:** This document defines the agreed architecture for generating Production Support runbooks from Java/Spring Boot service repositories using `idfc-coder`.

> **Core rule: AI discovers repository facts. Java validates, normalizes, maps, renders, gates and publishes. Java must never invent repository facts.**

This document should be used as the implementation guide for `idfc-coder` and for future changes to the AI Runbook Service.

---

# 1. Objective

For every service repository, generate an evidence-backed Production Support Runbook that can eventually be published to Confluence.

The system must:

- analyze the requested repository and commit fresh;
- extract only repository-proven facts;
- preserve evidence for extracted facts;
- validate AI output deterministically;
- prevent invented troubleshooting or operational information;
- map proven facts into a fixed set of Production Support sections;
- render Markdown and Confluence-ready HTML;
- block unsafe or low-quality output from publication;
- publish only the runbook corresponding to the exact deployed commit.

The system must work across many services without service-specific Java hardcoding.

---

# 2. Core Architecture

```text
Repository / Commit
        |
        v
idfc-coder
AI FACT EXTRACTION
        |
        v
runbook-data.json
runbook-evidence.json
security-findings.json
        |
        v
JAVA VALIDATION
        |
        v
JAVA NORMALIZATION
        |
        v
JAVA SECTION FACT MAPPING
        |
        v
JAVA RENDERING
        |
        v
QUALITY GATE
        |
        v
RUNBOOK.md
confluence-body.html
        |
        v
CONFLUENCE PUBLISHING
```

## AI responsibility

AI determines:

- what exists in the repository;
- what the code/configuration actually does;
- what evidence proves each fact.

## Java responsibility

Java determines:

- whether JSON structure is valid;
- whether evidence is present and valid;
- whether output is safe;
- how facts are normalized;
- how one proven fact may support multiple support sections;
- whether the result is publishable;
- how the runbook is formatted and published.

## Java must NOT determine

Java must never invent:

- error causes;
- business meaning;
- recovery behavior;
- support actions;
- authentication failure reasons;
- database failure reasons;
- Kafka behavior;
- API response meaning;
- transaction states;
- retry behavior;
- operational troubleshooting advice;

unless those facts already exist in canonical AI-extracted data with evidence.

---

# 3. Fresh Analysis Rule

Every repository analysis is independent.

```text
New repository
    -> fresh analysis from source

Same repository, new commit
    -> fresh analysis from source

Previous runbook
    -> comparison/diff only
```

Previous extraction output must never be supplied to AI as the truth about the current commit.

Previous results may be used only for:

- semantic diff;
- changed-section detection;
- release impact comparison;
- publication decision support.

This prevents stale facts from contaminating a new service or a new commit.

---

# 4. Repository Input Modes

The deterministic core pipeline is the same regardless of repository input mode.

## 4.1 BITBUCKET mode

Used for local/manual testing and future pipeline execution.

Inputs may include:

```text
repositoryUrl
branch
commitSha
serviceId
environment
```

### Local/manual behavior

If no exact commit is supplied:

```text
repository URL + branch/default branch
        |
        v
resolve remote HEAD
```

The resolved commit becomes the authoritative analyzed commit.

### Pipeline behavior

For CI/CD:

```text
exact commitSha is mandatory
```

The pipeline must never silently replace the supplied commit with the latest branch commit.

## 4.2 LOCAL_PATH mode

Used for local development/testing.

```text
local repository path
        |
        v
resolve local HEAD
        |
        v
fresh extraction
```

The extraction contract and output format must be identical to BITBUCKET mode.

## 4.3 PREGENERATED_FILES mode

Used when the three AI artifacts already exist.

Java must not overwrite supplied artifacts with templates. They proceed directly to deterministic validation.

---

# 5. Template-First Extraction

AI must not invent JSON structure.

Before `idfc-coder` is invoked for a fresh extraction, Java creates three fresh canonical template files inside the isolated job workspace.

```text
build/runbook-artifacts/
  <serviceId>/
    <jobId>/
      extraction/
        runbook-data.json
        runbook-evidence.json
        security-findings.json
```

Canonical templates are bundled under:

```text
src/main/resources/runbook-spec/templates/
```

Expected template resources:

```text
runbook-data.template.json
runbook-evidence.template.json
security-findings.template.json
```

Each job receives completely fresh copies. No previous service data is reused.

---

# 6. Why Template-First Exists

LLM output can vary between runs. Without template-first extraction, AI may create structural drift such as:

```text
serviceName
```

instead of:

```text
service.name
```

or put `scanStatus` at the wrong level.

Therefore:

```text
AI controls FACT VALUES
Java controls JSON STRUCTURE
```

AI is instructed to fill the existing structure only.

It must not:

- rename fields;
- flatten nested objects;
- move fields;
- create alternate field names;
- delete required fields;
- change fixed contract metadata;
- invent a second representation of the same concept.

---

# 7. Canonical AI Artifacts

Exactly three AI-owned artifacts are mandatory.

## 7.1 `runbook-data.json`

Contains canonical extracted service facts.

Examples of fact groups may include:

- service identity;
- business flows;
- APIs;
- business rules;
- response/error mappings;
- data lineage;
- state transitions;
- downstream dependencies;
- datastore facts;
- Kafka consumers/producers;
- resilience;
- configuration;
- retention;
- duplicate protection;
- observability;
- log signatures;
- support health checks;
- tracing identifiers;
- unknown incident context;
- escalation evidence.

Exact structure is defined only by the authoritative schema.

## 7.2 `runbook-evidence.json`

Contains evidence for repository-proven facts.

The canonical evidence model includes schema-defined properties such as:

```text
factId
factType
fact
provenance
confidence
sourceEvidence
```

Source evidence includes repository location details such as:

```text
file
lineStart
lineEnd
```

A factual statement in the runbook must be traceable to canonical extracted data and corresponding repository evidence.

## 7.3 `security-findings.json`

Always mandatory, even when empty.

Sensitive values must never be copied into generated documentation.

---

# 8. Contract and Schema Ownership

The authoritative contract is version `2.1`.

The bundled schemas are the source of truth:

```text
src/main/resources/runbook-spec/schema/
```

Expected schema resources:

```text
runbook-data.schema.json
runbook-evidence.schema.json
security-findings.schema.json
```

The validator must remain strict.

Do NOT:

- weaken schemas because AI produced the wrong format;
- accept aliases such as `serviceName` for `service.name`;
- silently relocate fields;
- dynamically guess AI intent;
- add arbitrary compatibility mappings.

Correct response to invalid AI structure:

```text
fail validation
```

Correct permanent fix:

```text
improve extraction contract/template/prompt
```

not:

```text
weaken validation
```

---

# 9. Prompt Assembly

The active `idfc-coder` prompt should deterministically include:

```text
extractor prompt
+
extraction contract
+
platform context
+
support safety policy
+
canonical template structure
+
schema requirements
+
repository/commit execution context
```

The prompt must explicitly tell AI:

```text
Analyze this repository/commit fresh.

Populate the three existing canonical JSON files.

Do not create alternate JSON structures.
Do not rename or move canonical fields.
Do not invent repository facts.
Every populated fact must be supported by repository evidence.
```

The prompt fingerprint should change when the extraction contract, templates, schema guidance or policy changes.

---

# 10. What idfc-coder Must Inspect

For Java/Spring Boot services, inspect relevant repository areas where present.

## Service/API structure

- controllers;
- request/response DTOs;
- validation annotations;
- service classes;
- handlers;
- filters/interceptors;
- API routing.

## Error handling

- `@ControllerAdvice`;
- `@ExceptionHandler`;
- `@ResponseStatus`;
- `ResponseEntity`;
- global exception handlers;
- custom exceptions;
- validation handlers;
- error response DTOs.

## Downstream integration

- `RestClient`;
- `WebClient`;
- Feign;
- HTTP clients;
- timeouts;
- downstream error handling;
- status interpretation.

## Persistence

- repositories;
- JPA entities;
- SQL;
- Flyway/Liquibase;
- Aerospike models;
- datastore lookup keys;
- support-relevant fields.

## Kafka

- listeners;
- producers;
- topics;
- consumer groups;
- retry/DLT/error handling;
- acknowledgement behavior.

## Configuration

- `application.yml`;
- `application.properties`;
- environment placeholders;
- configuration classes;
- timeout/retry settings;
- feature flags.

## Resilience

- circuit breakers;
- retries;
- cache fallback;
- fallback behavior;
- automatic failure handling.

## Observability

- log signatures;
- correlation identifiers;
- tracing;
- metrics;
- actuator endpoints;
- health checks.

AI should extract only what can actually be supported by repository evidence.

---

# 11. No Hardcoded Factual Enrichment

This is a critical rule.

Java must NOT contain generic factual mappings such as:

```text
400 -> malformed request
401 -> OAuth token expired
500 -> database failure
503 -> connection pool exhausted
```

unless those exact facts were extracted from the repository.

Java must not contain generic fallback maps such as `ERROR_CODE_CAUSES` for generating Production Support facts.

Example: if the repository proves only HTTP `500`, then the runbook may show:

```text
Error Code: 500
Possible Cause: Not found in repository
How to Confirm: Not found in repository
Support Action: Not found in repository
```

Java must not invent:

```text
Database connection failure
Unexpected exception
Check database connectivity
```

unless those facts are present in canonical AI output with evidence.

---

# 12. Java Is Not a Second Source-Code Analyzer

Another critical boundary:

`RunbookNormalizer` must not open repository source files and derive new support facts.

Do NOT implement Java logic such as:

```text
parse @ExceptionHandler
map exception class to HTTP code
inspect source code to derive recovery behavior
inspect source code to create support actions
```

Otherwise the architecture becomes:

```text
AI extractor
+
Java extractor
```

which creates conflicting sources of truth.

Correct flow:

```text
Source Repository
     |
     v
idfc-coder
     |
     v
Canonical Facts + Evidence
     |
     v
Java validation and presentation
```

Evidence validation may verify:

- cited file exists;
- cited line range is valid;
- evidence metadata is structurally correct.

But Java must not reinterpret source code and create new facts.

---

# 13. Deterministic Validation Pipeline

After AI extraction completes, Java validates in stages.

```text
AI output
   |
   v
Schema Validation
   |
   v
Evidence Validation
   |
   v
Secret/Sensitive Data Validation
   |
   v
Support Safety Validation
   |
   v
Normalization
   |
   v
Section Fact Mapping
   |
   v
Renderer Validation
```

---

# 14. Schema Validation

Validate all three canonical artifacts against their bundled JSON schemas.

If invalid:

```text
RUNBOOK_SCHEMA_INVALID
```

The pipeline stops.

Do not:

- automatically reshape arbitrary AI output;
- weaken the validator;
- perform endless extraction repair loops;
- invent missing facts in Java.

Improve the prompt/template contract instead.

---

# 15. Evidence Validation

Every important extracted fact should have corresponding repository evidence.

Evidence validation checks that:

- fact identifiers are valid;
- evidence exists where required;
- file references are valid;
- line ranges are valid;
- confidence values are valid;
- evidence structure follows contract 2.1.

Evidence validation does NOT decide whether source code logically implies a new support fact. That remains the AI extractor's responsibility.

---

# 16. Secret/Sensitive Data Handling

Secrets must never appear in generated runbooks.

Configuration should preserve:

```text
propertyKey
external configKey
repository default
runtime resolution state
```

Example:

```text
${STATUS_API_TIMEOUT}
```

should be represented as runtime external with the known config key and `CHECK_CONFIG_PORTAL` semantics.

Sensitive configuration should use:

```text
PROTECTED_CHECK_CONFIG_PORTAL
```

Never print the secret value.

---

# 17. Configuration Semantics

Canonical runtime semantics:

```text
Not Applicable
```

means the concept genuinely does not apply.

```text
Not found in repository
```

means the repository was inspected and the fact was absent.

```text
Could not be confirmed from repository
```

means evidence was insufficient.

```text
Check Config Portal
```

means the exact external config key is known but runtime value is external.

```text
Protected - check Config Portal
```

means the sensitive config key is known, but its value must never be emitted.

Examples:

```text
${KEY}
```

-> `CHECK_CONFIG_PORTAL`

```text
${KEY:10000}
```

-> config key `KEY`, repository default `10000`, runtime still external.

Direct literal:

```text
10000
```

-> `KNOWN_FROM_REPOSITORY`

---

# 18. Support Safety Policy

Production Support guidance must remain read-only.

The generated runbook must not instruct L1/L2 Support to:

- manually retry transactions;
- reprocess requests;
- replay Kafka events;
- change Kafka offsets;
- mutate production DB rows;
- mutate Aerospike records;
- force application state;
- trigger schedulers manually;
- change runtime configuration;
- expose secrets;
- restart/scale services as transaction recovery;
- manually reconcile state.

Safe guidance includes:

- inspect logs;
- inspect transaction state;
- inspect datastore evidence;
- inspect Kafka consumer lag;
- inspect Config Portal;
- inspect downstream status;
- collect correlation identifiers;
- escalate with evidence.

---

# 19. Normalization

Normalization must be deterministic.

Allowed normalization operations include:

- stable ordering;
- deduplication;
- whitespace cleanup;
- canonical unknown values;
- normalized configuration representation;
- stable identifiers;
- consistent formatting.

Normalization must NOT introduce new factual content.

---

# 20. Cross-Section Fact Mapping

A repository fact may be useful in multiple Production Support sections.

The AI should not be required to duplicate the same fact into many structures.

Instead:

```text
Canonical Facts
      |
      v
SectionFactMapper
      |
      v
23 Section View Models
```

Example: a proven downstream failure fact may support:

```text
Business Decision Rules
Response & Error Mapping
Transaction Lifecycle
Downstream Dependencies
Operational Errors
Unknown Incident Triage
```

The Java mapper may reuse the same proven fact in multiple sections.

It must not invent a new fact while doing so.

---

# 21. Frozen 23 Production Support Sections

The rendered runbook uses these exact sections in this order:

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

These headings are frozen.

---

# 22. Section Population Rule

A section should be populated from all relevant proven canonical facts.

Example:

## Transaction Lifecycle, States & Recovery

May consume proven facts from:

```text
stateTransitions
businessRules
downstream failurePropagation
resilience
logSignatures
escalationEvidence
```

## Operational Errors & Troubleshooting

May consume proven facts from:

```text
errorCodes
responseMappings
logSignatures
downstreamDependencies
resilience
businessRules
escalationEvidence
```

## Response & Error Mapping

May consume proven facts from:

```text
APIs
errorCodes
responseMappings
businessRules
downstream response interpretation
failure propagation
```

The mapper reuses existing facts. It must not generate generic causes/actions.

---

# 23. Empty Section Semantics

Do not display a false:

```text
Not found in repository
```

when relevant facts exist elsewhere in canonical data.

Before declaring a section empty, the mapper must check all approved fact sources for that section.

However, the mapper must also avoid manufacturing completeness.

Example for `Data Origin, Reference Data & Transformation`:

```text
Data Origin: found
Reference Data: Not found in repository
Transformation: found
```

Do not convert partial evidence into a fully populated claim.

---

# 24. Quality Gate

The quality gate decides whether the runbook is publishable.

Possible outcomes may include:

```text
PASS
PASS_WITH_WARNINGS
RENDER_ALLOWED_PUBLISH_BLOCKED
FAILED
```

Typical publication requirements:

```text
schemaValidation = true
evidenceValidation = true
secretValidation = true
safetyValidation = true
rendererValidation = true
scanStatus = COMPLETE
qualityGate = PASS
publishEligible = true
```

A partial scan may render for local inspection but should not be automatically published to production documentation.

---

# 25. Job State Flow

Typical job lifecycle:

```text
RECEIVED
   |
   v
PREPARING_WORKSPACE
   |
   v
EXTRACTING
   |
   v
VALIDATING
   |
   v
NORMALIZING
   |
   v
DIFFING
   |
   v
RENDERING
   |
   +----------------------------+
   |                            |
   v                            v
READY_TO_PUBLISH      NO_OPERATIONAL_CHANGE

Failure paths:
FAILED
RENDERED_PUBLISH_BLOCKED
```

Publication lifecycle:

```text
READY_TO_PUBLISH
   |
   v
PUBLISHING
   |
   v
PUBLISHED
```

---

# 26. Generated Artifacts

Typical artifact tree:

```text
build/runbook-artifacts/
  <serviceId>/
    <jobId>/
      extraction/
        runbook-data.json
        runbook-evidence.json
        security-findings.json
        idfc-coder.stdout.log
        idfc-coder.stderr.log

      validation/
        validation-report.json

      normalized/
        normalized-runbook-data.json

      diff/
        runbook-delta.json
        section-fingerprints.json

      report/
        generation-report.json

      render/
        RUNBOOK.md
        confluence-body.html
        API-CATALOG.md
        BUSINESS-RULES.md
        CONFIGURATION-CATALOG.md
        OBSERVABILITY-CATALOG.md
        ARCHITECTURE.md
        RELEASE-IMPACT.md
```

`RUNBOOK.md` is the main human-readable runbook.

`confluence-body.html` is the Confluence-ready rendering.

---

# 27. Diff Behavior

The current commit is always authoritative.

Previous normalized output is used only for comparison.

```text
Current fresh normalized facts
        |
        +---- compare ---- Previous normalized facts
        |
        v
runbook-delta.json
section-fingerprints.json
```

The diff may determine:

- changed sections;
- operational impact;
- whether publication is required;
- whether no operational change occurred.

Previous output must never alter the current extraction result.

---

# 28. Confluence Publishing

Confluence publishing happens only after the runbook is successfully generated and passes the publication gate.

Preferred flow:

```text
Generate runbook
      |
      v
qualityGate = PASS
publishEligible = true
      |
      v
resolve exact configured Confluence pageId
      |
      v
read current page + version
      |
      v
replace AI-managed block only
      |
      v
increment Confluence version
      |
      v
publish
```

Do not search by similar page titles.

Each service should map to an exact Confluence page ID.

Manual notes must be preserved.

Recommended managed markers:

```html
<!-- AI-RUNBOOK-START -->

generated content

<!-- AI-RUNBOOK-END -->
```

Anything outside the managed block remains human-owned.

---

# 29. Local Publishing Behavior

For local/manual testing:

```text
runbook generation
      |
      v
READY_TO_PUBLISH
      |
      v
optional explicit developer action
```

Do not automatically publish to Confluence merely because a developer ran the local script.

A local script may later offer:

```text
Publish to Confluence? [y/N]
```

---

# 30. CI/CD Pipeline Publishing Behavior

Pipeline execution is separate from local developer execution.

Preferred production flow:

```text
CI builds exact commit
      |
      v
Runbook service analyzes exact commit
      |
      v
runbook becomes READY_TO_PUBLISH
      |
      v
application deploys
      |
      v
production deployment succeeds
      |
      v
verify deployedCommit == analyzedCommit
      |
      v
publish runbook
```

Never publish documentation for commit `B` if production is actually running commit `A`.

Pipeline execution must use:

- non-interactive credentials;
- machine/service identity;
- exact commit;
- no developer shell or SSO assumptions.

---

# 31. Local vs Pipeline Separation

The deterministic core is shared. Adapters are different.

```text
                   +--------------------+
                   | Deterministic Core |
                   +--------------------+
                     /                \
                    /                  \
                   v                    v
          Local Adapter           Pipeline Adapter
          Developer SSO           Machine identity
          Interactive             Non-interactive
          Branch HEAD allowed     Exact commit required
```

Do not scatter `if(local)` / `if(pipeline)` logic throughout business logic.

Keep execution-mode concerns at adapter boundaries.

---

# 32. idfc-coder Process Invocation

`idfc-coder` should be invoked directly by `ProcessBuilder`.

Conceptually:

```text
idfc-coder
-p
<assembled prompt as one argument>
```

Do not invoke through a shell.

Do not split the prompt into arbitrary command-line words.

Capture:

```text
stdout
stderr
exit code
```

Diagnostic log files should be created even when `idfc-coder` fails.

A failed agent invocation must produce a clear:

```text
RUNBOOK_AGENT_FAILED
```

with safe diagnostics.

Do not expose credentials, tokens or the full sensitive prompt in failure responses.

---

# 33. Agent Failure Handling

If `idfc-coder` fails:

```text
EXTRACTING
    |
    v
RUNBOOK_AGENT_FAILED
```

The pipeline stops.

Java does not continue to normalization/rendering from partially populated templates.

Diagnostic files should remain available.

Do not silently treat initial templates as successful extraction output.

---

# 34. Schema Failure Handling

If AI returns JSON that does not validate:

```text
EXTRACTING
    |
    v
VALIDATING
    |
    v
RUNBOOK_SCHEMA_INVALID
```

The system stops.

Do not:

- weaken the validator;
- repair arbitrary field names;
- perform endless AI retries;
- invent missing facts in Java.

Improve the prompt/template contract instead.

---

# 35. Evidence-Only Troubleshooting Rule

Every factual troubleshooting statement must come from canonical extracted facts with evidence.

Allowed example:

```text
Log Signature:
"Transaction already marked as SUCCESS"

Source:
TxnStatusCheckConsumer.java
```

Allowed example:

```text
Failure Propagation:
Transaction marked FAILED and utilization restored
```

only when repository evidence proves it.

Not allowed:

```text
500 -> likely database issue
```

unless repository evidence proves the database issue.

---

# 36. Unknown Incident Triage

Unknown incident guidance should be assembled from proven support facts.

It may include read-only inspection steps such as:

- inspect transaction state;
- inspect relevant datastore record;
- inspect logs by correlation ID;
- inspect consumer lag;
- inspect downstream availability;
- inspect known runtime configuration;
- collect evidence for escalation.

It must not tell Support to alter production state.

---

# 37. Support Responsibility

The runbook should make the operational boundary explicit.

Example policy:

```text
L1/L2 may inspect and collect evidence.

L1/L2 must not:
- replay events
- change offsets
- mutate production data
- manually reprocess transactions
- change runtime configuration
- force state transitions
```

Escalation should contain the evidence needed by engineering teams.

---

# 38. Testing Requirements

The test suite should protect the architecture.

## Template/schema

- all three templates validate;
- each fresh job gets independent templates;
- previous service data is not reused;
- PREGENERATED_FILES is not overwritten.

## Extraction boundaries

- `RunbookNormalizer` does not read repository source files;
- normalizer does not parse `@ExceptionHandler`;
- Java does not infer HTTP codes from exception classes;
- no hardcoded HTTP cause/action mappings exist.

## Evidence

- proven facts are preserved;
- missing fields remain canonical unknowns;
- evidence references validate;
- unsupported factual fields do not magically appear.

## Section mapping

- one proven fact can support multiple relevant sections;
- sections do not incorrectly say `Not found in repository` when relevant canonical facts exist;
- mapping does not invent new facts.

## Safety

- prohibited production actions are rejected;
- negated statements such as `Do not manually update database rows` remain allowed;
- sensitive values are never rendered.

## Renderer

- exactly 23 frozen headings;
- exact order;
- renderer contains only approved facts;
- no Java fallback facts.

## Repository modes

- BITBUCKET works;
- LOCAL_PATH works;
- PREGENERATED_FILES works;
- exact pipeline commit takes precedence.

---

# 39. Design Anti-Patterns to Reject

## Anti-pattern 1: Hardcoded support knowledge

```text
500 -> DB failure
401 -> token expired
```

Rejected.

## Anti-pattern 2: Java source-code extraction

```text
Normalizer parses source code
```

Rejected.

## Anti-pattern 3: Schema weakening

```text
AI produced serviceName, so accept serviceName
```

Rejected.

## Anti-pattern 4: Previous run reused as current truth

Rejected.

## Anti-pattern 5: AI directly publishes Confluence

Rejected.

## Anti-pattern 6: Fuzzy Confluence page search

Rejected.

## Anti-pattern 7: Unsafe support recovery actions

Rejected.

## Anti-pattern 8: Renderer invents helpful factual text

Rejected.

---

# 40. Final Ownership Model

```text
SOURCE REPOSITORY
    owns current technical truth

IDFC-CODER
    owns repository fact discovery

RUNBOOK-DATA.JSON
    owns canonical extracted facts

RUNBOOK-EVIDENCE.JSON
    owns provenance

JAVA VALIDATORS
    own trust/gating

JAVA NORMALIZER
    owns deterministic cleanup only

SECTION FACT MAPPER
    owns deterministic reuse of proven facts

RENDERER
    owns presentation

QUALITY GATE
    owns publication eligibility

CONFLUENCE PUBLISHER
    owns controlled documentation update
```

No layer should take over another layer's responsibility.

---

# 41. Golden End-to-End Flow

```text
1. Receive repository request
        |
2. Resolve exact repository commit
        |
3. Create isolated job workspace
        |
4. Create three fresh canonical templates
        |
5. Assemble contract/schema/safety prompt
        |
6. Invoke idfc-coder once
        |
7. idfc-coder analyzes repository fresh
        |
8. idfc-coder fills:
       runbook-data.json
       runbook-evidence.json
       security-findings.json
        |
9. Validate schema
        |
10. Validate evidence
        |
11. Validate secrets
        |
12. Validate support safety
        |
13. Normalize deterministically
        |
14. Map proven facts to 23 support sections
        |
15. Compare with previous normalized run for diff only
        |
16. Render RUNBOOK.md
        |
17. Render confluence-body.html
        |
18. Validate rendered contract
        |
19. Apply quality gate
        |
20. READY_TO_PUBLISH
        |
21. After successful production deployment,
    verify analyzed commit == deployed commit
        |
22. Publish exact AI-managed section
    to exact configured Confluence page ID
```

---

# 42. One-Line Rule for Future Developers / AI Agents

> **Do not make the runbook look smarter than the repository evidence.**

If the repository does not prove a fact, the correct output is an explicit unknown, not a plausible guess.

---

# 43. Agreed Architecture Decisions

The following decisions are considered fixed unless deliberately revisited:

- Contract version remains `2.1`.
- Three AI artifacts are mandatory.
- Template-first extraction is mandatory.
- Every service/commit is analyzed fresh.
- AI is the sole repository fact extractor.
- Java must not analyze source code to derive new runbook facts.
- Java must not contain generic factual support fallbacks.
- Strict schema validation remains.
- Evidence validation remains.
- Support safety validation remains.
- Cross-section deterministic fact reuse is allowed.
- 23 support headings remain frozen.
- Previous output is diff-only.
- Local and pipeline execution are separate adapters.
- Pipeline exact commit is mandatory.
- AI never directly controls Confluence publication.
- Exact Confluence page ID is required.
- Manual Confluence content must be preserved.
- Publication occurs only after quality gate and deployed-commit verification.

---

# 44. Expected Behavior on a New Service

For a completely different service such as:

```text
api-virtualization-service
customer-service
payments-service
account-service
```

the process remains identical:

```text
fresh repository analysis
+
same fixed schemas/templates
+
same validators
+
same 23-section mapper
```

Only repository facts change.

The contract, structure and trust model remain constant.

---

# 45. Expected Behavior When Information Is Missing

Missing information is not a failure by itself.

Example:

```text
Rate Limiting:
Not found in repository
```

is valid if the repository was fully inspected and no rate-limiting implementation/configuration exists.

The system should prefer:

```text
accurate incomplete documentation
```

over:

```text
complete-looking guessed documentation
```

That principle is central to the platform.

---

# 46. Instruction to idfc-coder When Reading This Document

When implementing or modifying the AI Runbook Service, use this document as the architectural contract.

Before making a change, classify it into one of these responsibilities:

```text
AI extraction
Java validation
Java normalization
Section mapping
Rendering
Quality gating
Publishing
```

If a proposed Java change creates a new repository fact, reject that design and move the responsibility back to AI extraction.

If a proposed AI change alters publication, gating, schema enforcement or Confluence page selection, reject that design and keep those responsibilities deterministic in Java.

When uncertain, preserve the core rule:

> **AI discovers. Java verifies and presents.**

