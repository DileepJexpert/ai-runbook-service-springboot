package com.idfc.ai.runbook.repository; public interface RepositoryWorkspaceProvider { RepositoryWorkspace prepare(String localPath,String expectedCommit); }
