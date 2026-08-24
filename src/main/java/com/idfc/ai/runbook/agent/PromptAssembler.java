package com.idfc.ai.runbook.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Deterministically assembles extraction-only instructions from versioned components. */
public final class PromptAssembler {
  public record AssembledPrompt(String text, String sha256) {}
  private PromptAssembler() {}

  public static AssembledPrompt assemble(
      String promptV3,
      String extractionContract,
      String platformContext,
      String configExtractionPolicy,
      String evidencePolicy,
      String safetyPolicy,
      String qualityExpectations,
      String regulatoryEvidencePolicy,
      String runbookDataTemplate,
      String runbookEvidenceTemplate,
      String securityFindingsTemplate
  ) {
    String text = String.join("\n\n", List.of(
        promptV3.strip(),
        extractionContract.strip(),
        platformContext.strip(),
        configExtractionPolicy.strip(),
        evidencePolicy.strip(),
        safetyPolicy.strip(),
        qualityExpectations.strip(),
        regulatoryEvidencePolicy.strip(),
        "AUTHORITATIVE TEMPLATES FOR EXTRACTION:\n\n=== runbook-data.template.json ===\n" + runbookDataTemplate.strip() +
        "\n\n=== runbook-evidence.template.json ===\n" + runbookEvidenceTemplate.strip() +
        "\n\n=== security-findings.template.json ===\n" + securityFindingsTemplate.strip()
    )) + "\n";
    return new AssembledPrompt(text, sha256(text));
  }

  public static AssembledPrompt assemble(
      String promptV3,
      String extractionContract,
      String platformContext,
      String configExtractionPolicy,
      String evidencePolicy,
      String safetyPolicy,
      String qualityExpectations,
      String regulatoryEvidencePolicy
  ) {
    String text = String.join("\n\n", List.of(
        promptV3.strip(),
        extractionContract.strip(),
        platformContext.strip(),
        configExtractionPolicy.strip(),
        evidencePolicy.strip(),
        safetyPolicy.strip(),
        qualityExpectations.strip(),
        regulatoryEvidencePolicy.strip()
    )) + "\n";
    return new AssembledPrompt(text, sha256(text));
  }

  public static AssembledPrompt assemble(String extractionContract, String evidencePolicy, String safetyPolicy, String platformContext) {
    String text = String.join("\n\n", List.of(extractionContract.strip(), evidencePolicy.strip(), safetyPolicy.strip(), platformContext.strip())) + "\n";
    return new AssembledPrompt(text, sha256(text));
  }

  private static String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder();
      for (byte b : digest) out.append(String.format("%02x", b));
      return out.toString();
    } catch (Exception exception) {
      throw new IllegalStateException("RUNBOOK_PROMPT_FINGERPRINT_FAILED", exception);
    }
  }
}
