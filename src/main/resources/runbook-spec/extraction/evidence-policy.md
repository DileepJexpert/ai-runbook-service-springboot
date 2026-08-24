# Evidence Policy 2.1

Every extracted fact has repository location evidence and confidence. Scan completeness and individual fact confidence are independent. Never infer a runtime value, external configuration key, regulatory basis, or transaction participation without repository evidence.

## Exclusion of Generated Build Outputs
Generated build outputs (`**/build/**`, `**/target/**`, `**/out/**`, `**/.gradle/**`, `**/node_modules/**`) are strictly excluded from AI source scanning and evidence citations. Evidence must cite authoritative source files (e.g. `src/main/...`, `src/test/...`, checked-in deployment/configuration files) and must never cite generated resource copies.
