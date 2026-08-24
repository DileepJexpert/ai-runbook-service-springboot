package com.idfc.ai.runbook.repository;

import com.idfc.ai.runbook.api.dto.CreateJobRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class LocalRepositoryWorkspaceProvider implements RepositoryWorkspaceProvider {
  private final RemoteGitResolver remoteGitResolver;

  public LocalRepositoryWorkspaceProvider(RemoteGitResolver remoteGitResolver) {
    this.remoteGitResolver = remoteGitResolver;
  }

  @Override
  public RepositoryWorkspace prepare(CreateJobRequest.Repository repository, String expectedCommit) {
    if (repository == null || repository.mode() == null || repository.mode().isBlank()) {
      throw new IllegalArgumentException("RUNBOOK_REPOSITORY_INVALID: missing repository mode");
    }

    String mode = repository.mode().toUpperCase();
    if ("LOCAL_PATH".equals(mode)) {
      String localPath = repository.localPath();
      if (localPath == null || localPath.isBlank()) {
        throw new IllegalArgumentException("RUNBOOK_REPOSITORY_INVALID: missing localPath");
      }
      try {
        Path path = Path.of(localPath).toRealPath();
        if (!Files.isDirectory(path)) {
          throw new IllegalArgumentException("RUNBOOK_REPOSITORY_INVALID: directory not found");
        }
        Process p = new ProcessBuilder("git", "rev-parse", "HEAD").directory(path.toFile()).start();
        String sha = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (p.waitFor() != 0 || sha.isBlank()) {
          throw new IllegalArgumentException("RUNBOOK_REPOSITORY_INVALID: not a Git checkout");
        }
        if (expectedCommit != null && !expectedCommit.isBlank() && !sha.startsWith(expectedCommit) && !expectedCommit.startsWith(sha)) {
          throw new IllegalArgumentException("RUNBOOK_COMMIT_MISMATCH: local HEAD " + sha + " does not match expected " + expectedCommit);
        }
        return new RepositoryWorkspace("LOCAL_PATH", path, sha, null, null);
      } catch (IOException | InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalArgumentException("RUNBOOK_REPOSITORY_INVALID", e);
      }
    } else if ("BITBUCKET".equals(mode)) {
      String url = repository.url();
      if (url == null || url.isBlank()) {
        throw new IllegalArgumentException("RUNBOOK_REPOSITORY_INVALID: missing Bitbucket repository url");
      }
      String branch = repository.branch();
      String resolvedCommit;
      if (expectedCommit != null && !expectedCommit.isBlank()) {
        // CI/CD / PRODUCTION: exact commit supplied wins
        resolvedCommit = expectedCommit;
      } else {
        // LOCAL / MANUAL TEST: resolve remote HEAD for branch (or default branch)
        resolvedCommit = remoteGitResolver.resolveRemoteHead(url, branch);
      }
      return new RepositoryWorkspace("BITBUCKET", null, resolvedCommit, url, branch);
    } else {
      throw new IllegalArgumentException("RUNBOOK_REPOSITORY_INVALID: unsupported repository mode: " + repository.mode());
    }
  }
}
