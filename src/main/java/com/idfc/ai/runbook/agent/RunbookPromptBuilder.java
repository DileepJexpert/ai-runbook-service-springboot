package com.idfc.ai.runbook.agent;

import com.idfc.ai.runbook.config.RunbookProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@Component
public class RunbookPromptBuilder {
  private final RunbookProperties properties;

  public RunbookPromptBuilder(RunbookProperties properties) {
    this.properties = properties;
  }

  public String prompt() {
    return assembled().text();
  }

  public String promptFingerprint() {
    return assembled().sha256();
  }

  public String context() {
    return readSpecResource("context/platform-context.md");
  }

  public String extractionContract() {
    return readSpecResource("extraction/extraction-contract.md");
  }

  public String safetyPolicy() {
    return readSpecResource("extraction/safety-policy.md");
  }

  public String evidencePolicy() {
    return readSpecResource("extraction/evidence-policy.md");
  }

  public String configExtractionPolicy() {
    return readSpecResource("extraction/configuration-extraction.md");
  }

  public String qualityExpectations() {
    return readSpecResource("extraction/quality-expectations.md");
  }

  public String regulatoryEvidencePolicy() {
    return readSpecResource("extraction/regulatory-evidence-policy.md");
  }

  public String promptV3() {
    return readSpecResource("prompt/json-extractor-prompt-v3.txt");
  }

  public PromptAssembler.AssembledPrompt assembled() {
    return PromptAssembler.assemble(
        promptV3(),
        extractionContract(),
        context(),
        configExtractionPolicy(),
        evidencePolicy(),
        safetyPolicy(),
        qualityExpectations(),
        regulatoryEvidencePolicy()
    );
  }

  public String readSpecResource(String relativePath) {
    try {
      Resource resource;
      if (properties != null && properties.getSpecRoot() != null && !properties.getSpecRoot().isBlank()) {
        resource = new FileSystemResource(Path.of(properties.getSpecRoot()).resolve(relativePath));
      } else {
        resource = new ClassPathResource("runbook-spec/" + relativePath);
      }
      return resource.getContentAsString(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load spec resource: " + relativePath, e);
    }
  }
}
