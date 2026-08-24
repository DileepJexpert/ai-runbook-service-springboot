# Future merge

Copy the `com.idfc.ai.runbook` feature packages into the existing AI service. Replace only adapter implementations: `LocalIdfcCoderExecutor`, `LocalRepositoryWorkspaceProvider`, filesystem artifact and baseline stores, and the reference Confluence client. Reuse validators, normalizer, diff engine, renderers, manual-notes logic, typed API models, and orchestration. Integrate the bank's logging, security, Config Portal, checkout, and artifact facilities without inventing existing class names.
