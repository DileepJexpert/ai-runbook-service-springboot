# Semantic Diff Policy 2.0

Operational comparison ignores `pipeline.generatedAt`, `pipeline.buildNumber`, `pipeline.gitCommitSha`, and `pipeline.imageTag`. It compares canonical entities by stable ID and reports `ADDED`, `REMOVED`, and `MODIFIED` changes with old/new values. Section fingerprints are supporting evidence, not the complete diff.
