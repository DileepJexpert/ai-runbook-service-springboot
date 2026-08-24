package com.idfc.ai.runbook.repository;

import java.nio.file.Path;

public record RepositoryWorkspace(
    String mode,
    Path path,
    String commitSha,
    String url,
    String branch
) {
  public RepositoryWorkspace(Path path, String commitSha) {
    this("LOCAL_PATH", path, commitSha, null, null);
  }
}
