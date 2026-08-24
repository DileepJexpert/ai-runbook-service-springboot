package com.idfc.ai.runbook.agent;

import com.idfc.ai.runbook.config.RunbookProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@ConditionalOnProperty(name="runbook.agent.type", havingValue="local", matchIfMissing=true)
public class LocalIdfcCoderExecutor implements IdfcCoderExecutor {
  private final RunbookProperties properties;

  public LocalIdfcCoderExecutor(RunbookProperties properties) {
    this.properties = properties;
  }

  @Override
  public IdfcCoderResult execute(IdfcCoderRequest r) {
    try {
      Files.createDirectories(r.outputDirectory());

      StringBuilder instruction = new StringBuilder();
      instruction.append("Read the centralized prompt and context supplied below. ");
      if ("BITBUCKET".equalsIgnoreCase(r.repositoryMode())) {
        instruction.append("Analyze the remote Bitbucket repository at ").append(r.repositoryUrl());
        if (r.branch() != null && !r.branch().isBlank()) {
          instruction.append(" (branch: ").append(r.branch()).append(")");
        }
        instruction.append(" at commit ").append(r.commitSha()).append(". ");
      } else {
        instruction.append("Treat the current repository as authoritative. ");
      }
      instruction.append("Generate exactly ")
          .append(r.outputDirectory().resolve("runbook-data.json")).append(", ")
          .append(r.outputDirectory().resolve("runbook-evidence.json")).append(", and ")
          .append(r.outputDirectory().resolve("security-findings.json"))
          .append(". Do not generate RUNBOOK.md, access Confluence, publish, or modify source.\n\n");

      if ("BITBUCKET".equalsIgnoreCase(r.repositoryMode())) {
        instruction.append("TARGET REPOSITORY:\n")
            .append("URL: ").append(r.repositoryUrl()).append("\n")
            .append("BRANCH: ").append(r.branch() != null ? r.branch() : "DEFAULT").append("\n")
            .append("COMMIT: ").append(r.commitSha()).append("\n\n");
      }

      instruction.append("PROMPT:\n").append(r.prompt()).append("\n\n")
          .append("CONTEXT:\n").append(r.context());

      ProcessBuilder pb = new ProcessBuilder(properties.getAgent().getExecutable(), instruction.toString());
      if (r.repository() != null && Files.isDirectory(r.repository())) {
        pb.directory(r.repository().toFile());
      } else {
        pb.directory(r.outputDirectory().toFile());
      }

      Process process = pb.start();
      boolean done = process.waitFor(properties.getAgent().getTimeout().toMillis(), TimeUnit.MILLISECONDS);
      if (!done) {
        process.destroyForcibly();
        throw new IllegalStateException("RUNBOOK_AGENT_TIMEOUT");
      }

      String out = cap(process.getInputStream().readAllBytes());
      String err = cap(process.getErrorStream().readAllBytes());

      if (process.exitValue() != 0) {
        throw new IllegalStateException("RUNBOOK_AGENT_FAILED: " + err);
      }

      Path dataFile = r.outputDirectory().resolve("runbook-data.json");
      Path evidenceFile = r.outputDirectory().resolve("runbook-evidence.json");
      Path securityFile = r.outputDirectory().resolve("security-findings.json");

      if (!Files.isRegularFile(dataFile) || !Files.isRegularFile(evidenceFile)) {
        throw new IllegalStateException("RUNBOOK_EXTRACTION_MISSING");
      }

      if (!Files.isRegularFile(securityFile)) {
        Files.writeString(securityFile, "{\"contractVersion\": \"2.1\", \"findings\": []}\n", StandardCharsets.UTF_8);
      }

      return new IdfcCoderResult(out, err);
    } catch (IOException e) {
      throw new IllegalStateException("RUNBOOK_AGENT_FAILED", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("RUNBOOK_AGENT_TIMEOUT", e);
    }
  }

  private String cap(byte[] b) {
    return new String(b, 0, Math.min(b.length, properties.getAgent().getMaxCapturedLogBytes()), StandardCharsets.UTF_8);
  }
}
