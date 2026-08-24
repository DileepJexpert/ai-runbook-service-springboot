package com.idfc.ai.runbook.repository;

import com.idfc.ai.runbook.api.dto.CreateJobRequest;

public interface RepositoryWorkspaceProvider {
  RepositoryWorkspace prepare(CreateJobRequest.Repository repository, String expectedCommit);
}
