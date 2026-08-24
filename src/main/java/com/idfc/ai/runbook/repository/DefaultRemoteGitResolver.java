package com.idfc.ai.runbook.repository;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class DefaultRemoteGitResolver implements RemoteGitResolver {
  @Override
  public String resolveRemoteHead(String repositoryUrl, String branch) {
    if (repositoryUrl == null || repositoryUrl.isBlank()) {
      throw new IllegalArgumentException("RUNBOOK_REPOSITORY_INVALID: repository URL cannot be blank");
    }
    try {
      ProcessBuilder pb;
      if (branch != null && !branch.isBlank()) {
        pb = new ProcessBuilder("git", "ls-remote", repositoryUrl, "refs/heads/" + branch, branch);
      } else {
        pb = new ProcessBuilder("git", "ls-remote", repositoryUrl, "HEAD");
      }
      Process process = pb.start();
      boolean finished = process.waitFor(20, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        throw new IllegalStateException("RUNBOOK_REMOTE_RESOLUTION_TIMEOUT");
      }
      if (process.exitValue() != 0) {
        String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        throw new IllegalArgumentException("RUNBOOK_REPOSITORY_INVALID: remote resolution failed: " + err);
      }
      String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      if (out.isBlank()) {
        throw new IllegalArgumentException("RUNBOOK_BRANCH_NOT_FOUND: branch '" + branch + "' not found on remote " + repositoryUrl);
      }
      String firstLine = out.split("\\R")[0];
      String sha = firstLine.split("\\s+")[0].trim();
      if (sha.length() < 7) {
        throw new IllegalArgumentException("RUNBOOK_REMOTE_RESOLUTION_FAILED: invalid commit sha " + sha);
      }
      return sha;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalArgumentException("RUNBOOK_REPOSITORY_INVALID: failed to resolve remote git repository", e);
    }
  }
}
