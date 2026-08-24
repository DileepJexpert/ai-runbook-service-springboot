package com.idfc.ai.runbook.agent;

import java.nio.file.Path;

public record IdfcCoderRequest(
    Path repository,
    Path outputDirectory,
    String prompt,
    String context,
    Path preGeneratedData,
    Path preGeneratedEvidence,
    Path preGeneratedSecurityFindings,
    String repositoryMode,
    String repositoryUrl,
    String branch,
    String commitSha
) {
  public IdfcCoderRequest(Path repository, Path outputDirectory, String prompt, String context) {
    this(repository, outputDirectory, prompt, context, null, null, null, "LOCAL_PATH", null, null, null);
  }

  public IdfcCoderRequest(Path repository, Path outputDirectory, String prompt, String context, Path preGeneratedData, Path preGeneratedEvidence) {
    this(repository, outputDirectory, prompt, context, preGeneratedData, preGeneratedEvidence, null, "LOCAL_PATH", null, null, null);
  }

  public IdfcCoderRequest(Path repository, Path outputDirectory, String prompt, String context, Path preGeneratedData, Path preGeneratedEvidence, Path preGeneratedSecurityFindings) {
    this(repository, outputDirectory, prompt, context, preGeneratedData, preGeneratedEvidence, preGeneratedSecurityFindings, "LOCAL_PATH", null, null, null);
  }
}
