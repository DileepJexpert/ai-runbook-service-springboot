# Architecture

The standalone Spring Boot service implements the Java side of Service Intelligence contract 2.1. Compatibility filenames remain `runbook-data.json` and `runbook-evidence.json`, but they are canonical Service Intelligence facts and evidence—not rendered documents.

```text
repository + exact commit
  -> job-specific extraction directory
  -> idfc-coder or LOCAL-only pre-generated adapter
  -> validation / safety / evidence checks
  -> normalization / stable IDs / semantic diff
  -> quality gate / generation report
  -> deterministic Support and supplemental artifacts
  -> explicit, guarded publication
```

AI is limited to extracted data/evidence and optional restricted `security-findings.json`. Java owns validation, quality decisions, `runbook-delta.json`, `generation-report.json`, renderers, baseline advancement, target resolution, and publishing eligibility.

Each job writes under `<artifact-root>/<serviceId>/<jobId>/`. Pre-generated local files are accepted only in the `local` profile, only under configured allowed roots, and are copied into the job extraction directory before processing. No process-global input JSON properties are used.

`PARTIAL` scan status does not alter individual fact confidence. It renders artifacts but resolves to `RENDERED_PUBLISH_BLOCKED`; it cannot enter the publish flow.

The current `ConfluencePublisher` is a guarded boundary, not an HTTP client. It rejects disabled publishing, unknown/protected targets, invalid modes, and undersized renderer output. A production `ConfluenceClient` adapter must add GET-before-write verification, marker preservation, backup, optimistic versioning, post-write verification, and no mutating retry before production enablement.
