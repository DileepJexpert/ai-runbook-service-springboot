# Canonical Service Intelligence Model 2.1

Version 2.1 is additive to 2.0. Existing entity names and filenames retain their 2.0 meaning.

New optional canonical entities include `configuration`, `metrics`, `transactionBoundaries`, `schedulers`, `architectureFacts`, `testEvidence`, and `dataClassificationCandidates`.

Relevant facts may carry `provenance` from: CODE, CODE_CONSTANT, CODE_DEFAULT, ANNOTATION, YAML, YAML_DEFAULT, PROPERTIES_FILE, ENV_PLACEHOLDER, HELM, KUBERNETES, CONFIGMAP_REFERENCE, SECRET_REFERENCE, CONFIG_PORTAL_REFERENCE, DATABASE_MIGRATION, BUILD_FILE, GENERATED_API_SPEC, TEST_SOURCE, HUMAN_METADATA, DERIVED_FROM_MULTIPLE_SOURCES, UNKNOWN.

Configuration supports `id`, `componentId`, `componentName`, `modulePath`, `deploymentUnit`, `logicalPurpose`, `propertyKey`, `configKey`, `repositoryValue`, `repositoryDefault`, `unit`, `allowedValues`, `valueSource`, `runtimeValueStatus`, `sensitive`, `supportLookupInstruction`, `evidence`, and `confidence`. In multi-module repositories, `componentId` participates in identity to prevent configuration scope collisions. Valid runtime statuses are KNOWN_FROM_REPOSITORY, REPOSITORY_DEFAULT, CHECK_CONFIG_PORTAL, PROTECTED_CHECK_CONFIG_PORTAL, RESOLVED_FROM_DEPLOYMENT_CONFIG, NOT_FOUND_IN_REPOSITORY, NOT_APPLICABLE, and UNRESOLVED. Sensitive values are never stored.

Business rules may have `regulatoryBasis` with status EXPLICIT_REFERENCE, HUMAN_METADATA, or NOT_ESTABLISHED. A reference is recorded only when evidence explicitly establishes it.
