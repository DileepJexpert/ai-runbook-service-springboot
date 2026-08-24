package com.idfc.ai.runbook.agent;

import com.idfc.ai.runbook.config.RunbookProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Local-only adapter for externally generated extraction JSON, such as a Codex laptop POC. */
@Component
@Profile("local")
@ConditionalOnProperty(name="runbook.agent.type", havingValue="file")
public class FileBasedIdfcCoderExecutor implements IdfcCoderExecutor {
  private final RunbookProperties properties;
  public FileBasedIdfcCoderExecutor(RunbookProperties properties) { this.properties = properties; }
  @Override public IdfcCoderResult execute(IdfcCoderRequest request) {
    Path data = path(request.preGeneratedData(), "dataPath");
    Path evidence = path(request.preGeneratedEvidence(), "evidencePath");
    Path security = optionalPath(request.preGeneratedSecurityFindings(), "securityFindingsPath");
    try {
      Files.createDirectories(request.outputDirectory());
      Files.copy(data, request.outputDirectory().resolve("runbook-data.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      Files.copy(evidence, request.outputDirectory().resolve("runbook-evidence.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      if (security != null) Files.copy(security, request.outputDirectory().resolve("security-findings.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return new IdfcCoderResult("Loaded externally generated extraction JSON", "");
    } catch (IOException exception) { throw new IllegalStateException("RUNBOOK_EXTRACTION_MISSING", exception); }
  }
  private Path path(Path configured, String property) {
    if (configured == null) throw new IllegalArgumentException("RUNBOOK_FILE_AGENT_INVALID: missing " + property);
    Path path = configured.toAbsolutePath().normalize();
    if (!Files.isRegularFile(path)) throw new IllegalArgumentException("RUNBOOK_FILE_AGENT_INVALID: configured file does not exist");
    boolean allowed = properties.getLocalInput().getAllowedRoots().stream()
      .map(root -> Path.of(root).toAbsolutePath().normalize())
      .anyMatch(path::startsWith);
    if (!allowed) throw new IllegalArgumentException("RUNBOOK_FILE_AGENT_INVALID: path is outside local-input allowed roots");
    return path;
  }
  private Path optionalPath(Path configured, String property) { return configured == null ? null : path(configured, property); }
}
