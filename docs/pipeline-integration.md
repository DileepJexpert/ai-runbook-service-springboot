# Pipeline integration

## Intended production flow

1. Create a generation job with repository identity and requested commit.
2. The job owns its extraction directory and provides the extraction-only assembled prompt to the agent.
3. The agent writes canonical facts/evidence (and optional restricted security findings) into that job directory.
4. Java validates, normalizes, computes stable IDs/fingerprints and `runbook-delta.json`, applies quality gates, writes `generation-report.json`, and renders artifacts.
5. A job reaches `READY_TO_PUBLISH` only when quality permits publication. `RENDERED_PUBLISH_BLOCKED` and `NO_OPERATIONAL_CHANGE` must not be published.
6. Deploy the application. Call publish only after deployment identity is confirmed to match the analyzed commit/image.
7. Advance the environment baseline only after a successful guarded publication.

## Local POC flow

Use `scripts/run-local-runbook-poc.ps1` with target repository, service ID, and job-specific pre-generated data/evidence paths. The script never invokes `/publish`. A single `local` profile service can process multiple repositories without restart when their input parents are configured as allowed roots.

## Artifact ownership

- Agent-owned: `runbook-data.json`, `runbook-evidence.json`, optional `security-findings.json`.
- Java-owned: validation report, normalized canonical JSON, section fingerprints/delta, generation report, Markdown, semantic HTML, catalogs, architecture view, and release impact.

Security findings remain restricted job artifacts and are never rendered into the general Support page.
