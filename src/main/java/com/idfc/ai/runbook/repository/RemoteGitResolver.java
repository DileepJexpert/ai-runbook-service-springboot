package com.idfc.ai.runbook.repository;

public interface RemoteGitResolver {
  String resolveRemoteHead(String repositoryUrl, String branch);
}
