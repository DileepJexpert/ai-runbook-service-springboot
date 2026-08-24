# Spring Boot AI Production Support Runbook Service

A standalone Java 21 / Spring Boot 3.5 reference implementation for generating Production Support runbooks from AI-extracted JSON. It is intentionally independent of the bank's existing AI code-review service, so the reusable workflow can later be merged safely.

This Java project is fully self-contained. The bundled Service Intelligence specification, policies, prompts, and schemas are located in `src/main/resources/runbook-spec/`. No separate `ai-runbook-spec` checkout is required to run or test the Java service.

## Design

```text
POST job -> RepositoryWorkspaceProvider -> IdfcCoderExecutor -> extraction JSON
         -> validators -> normalizer -> semantic diff -> fixed renderers
         -> READY_TO_PUBLISH -> explicit publish after deployment succeeds
```

AI extracts only `runbook-data.json` and `runbook-evidence.json`. Java code owns validation, canonicalization, comparison, fixed headings, page resolution, and publishing. Current code is truth; baselines are comparison-only.

Contract 2.1 treats those compatibility-named JSON files as the canonical Service Intelligence Model. AI may also create restricted `security-findings.json`; only deterministic Java code creates `runbook-delta.json`, `generation-report.json`, and all rendered documents. Security findings are never included in the normal Production Support page.

Adapter boundaries are `IdfcCoderExecutor`, `RepositoryWorkspaceProvider`, `RunbookArtifactStore`, `BaselineStore`, and `RunbookTargetRegistry`. Future integration replaces local adapters without changing the domain pipeline. No existing bank code is referenced or modified.

## Local use

Java 21 is the compatibility baseline (the Maven compiler uses `--release 21`).

```powershell
mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

For a local POC with pre-generated extraction data, use the job-specific input script. It starts the service only if needed and never publishes:

```powershell
.\scripts\run-local-runbook-poc.ps1 `
  -TargetRepo "/path/to/target-repo" `
  -ServiceId "sample-service" `
  -InputDataPath "/path/to/runbook-test-output/runbook-data.json" `
  -InputEvidencePath "/path/to/runbook-test-output/runbook-evidence.json"
```

The script sends `extractionInput` in the POST body, and the local-only file adapter copies those files into `<artifact-root>/<service>/<job>/extraction/`. A running service can therefore process another repository with different inputs without restart or cross-contamination. Configure each permitted input parent in `runbook.local-input.allowed-roots`; these are allow-list roots, not process-global JSON paths.

The local profile defaults to Confluence disabled and uses the configured `idfc-coder` executable. Credentials belong only in `CONFLUENCE_BASE_URL`, `CONFLUENCE_USERNAME`, and `CONFLUENCE_API_TOKEN` when a real Confluence client is introduced.

## API

```bash
curl -X POST http://localhost:8080/api/v1/runbooks/jobs -H 'Content-Type: application/json' -d '{"serviceId":"payments-service","repository":{"mode":"LOCAL_PATH","localPath":"/path/to/repo","commitSha":"abc123"},"deployment":{"environment":"TEST","applicationVersion":"1.7.2","imageTag":"payments:1.7.2","buildNumber":"453","namespace":"payments","deploymentName":"payments"}}'
curl http://localhost:8080/api/v1/runbooks/jobs/{jobId}
curl -X POST http://localhost:8080/api/v1/runbooks/jobs/{jobId}/publish -H 'Content-Type: application/json' -d '{"mode":"TEST","deployedCommitSha":"abc123","deployedImageTag":"payments:1.7.2"}'
```

Publication accepts only `TEST` or `PRODUCTION`, resolves pages from configured targets, and validates deployed commit/image match. Local publishing is disabled by default. Artifacts are written under `build/runbook-artifacts/<service>/<job>/`.

See [architecture](docs/architecture.md), [pipeline integration](docs/pipeline-integration.md), and [merge guidance](docs/merge-into-existing-ai-service.md).
