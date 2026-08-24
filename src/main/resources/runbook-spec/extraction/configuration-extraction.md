# Configuration Extraction Contract 2.1

## Multi-Module and Component Scope
In multi-module / monorepo repositories, configuration facts must preserve deployable component scope. A property key (e.g. `server.port`) and external Config Portal key (e.g. `SERVER_PORT`) must not be collapsed into a single global fact across distinct modules.

Each configuration fact must include deterministic scope fields:
- `componentId` (e.g. `edges/sfdc-ingress-edge`, `capabilities/bureau`, `GLOBAL`)
- `componentName` (optional human-readable module/component name)
- `modulePath` (relative path to subproject/module directory)
- `deploymentUnit` (deployable artifact/service name)

Stable ID format for configuration facts:
`CONFIG:<COMPONENT_ID>:<PROPERTY_KEY>` (e.g. `CONFIG:edges/sfdc-ingress-edge:server.port`, `CONFIG:capabilities/bureau:server.port`)
For repository-global/root configuration facts, use an explicit deterministic `GLOBAL` or root scope (e.g. `CONFIG:GLOBAL:<PROPERTY_KEY>` or `CONFIG:<PROPERTY_KEY>`).

## Shared Config Key != Shared Value
Do not assume that because two modules use the same external key (`SERVER_PORT`), they share the same repository default or runtime value. External keys can be reused per deployment/environment. Preserve each deployable component's own:
- `propertyKey`
- `configKey`
- `repositoryValue`
- `repositoryDefault`
- `runtimeValueStatus`
- `evidence`

## Configuration Provenance and Status
Extract checked-in configuration keys and their repository value or default independently from the effective runtime value:
- For `${CLIENT_TIMEOUT:10000}`: retain `propertyKey=client.timeout`, `configKey=CLIENT_TIMEOUT`, `repositoryDefault=10000`, `valueSource=CONFIG_PORTAL_REFERENCE`, and `runtimeValueStatus=CHECK_CONFIG_PORTAL`.
- For sensitive values (passwords, tokens, private keys): retain only the key, logical purpose, source, and mark `runtimeValueStatus=PROTECTED_CHECK_CONFIG_PORTAL` with `sensitive=true`. Never emit raw secret values.

## Generated Build Output Exclusion
Generated build outputs must NOT be treated as independent authoritative source evidence.
By default exclude at least:
- `**/build/**`
- `**/target/**`
- `**/out/**`
- `**/.gradle/**`
- `**/node_modules/**`

Prefer `src/main/...`, `src/test/...`, and checked-in deployment/config files. Do not duplicate evidence from generated resource copies.

## Configuration Catalog Representation
The Configuration Catalog (`CONFIGURATION-CATALOG.md`) renders component/deployment unit visibility with columns:
`Component | Purpose | Property Key | Config Portal Key | Repository Value | Repository Default | Runtime Value Status | Sensitive | Support Lookup`
