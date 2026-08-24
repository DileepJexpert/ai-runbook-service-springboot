package com.idfc.ai.runbook.quality;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class QualityGateService {
  public QualityGateDecision evaluate(JsonNode data, List<String> validationErrors) {
    if (!validationErrors.isEmpty()) return new QualityGateDecision(QualityGateDecision.FAILED, false, List.of(), validationCodes(validationErrors));
    String version = data.path("contractVersion").asText();
    if (!"2.0".equals(version) && !"2.1".equals(version)) return new QualityGateDecision(QualityGateDecision.FAILED, false, List.of(), List.of("UNSUPPORTED_CONTRACT_VERSION"));
    if (hasSensitiveValue(data)) return new QualityGateDecision(QualityGateDecision.FAILED, false, List.of(), List.of("SECRET_VALUE_DETECTED"));
    if ("PARTIAL".equals(data.path("generator").path("scanStatus").asText())) return new QualityGateDecision(QualityGateDecision.RENDER_ALLOWED_PUBLISH_BLOCKED, false, List.of("PARTIAL_SCAN"), List.of());
    return new QualityGateDecision(QualityGateDecision.PASS, true, List.of(), List.of());
  }

  private boolean hasSensitiveValue(JsonNode data) {
    for (JsonNode item : data.path("configuration")) if (item.path("sensitive").asBoolean(false) && (!item.path("repositoryValue").isMissingNode() || !item.path("repositoryDefault").isMissingNode())) return true;
    return false;
  }
  private List<String> validationCodes(List<String> errors) {
    String joined=String.join(" ",errors).toLowerCase();
    if (joined.contains("secret")) return List.of("SECRET_VALUE_DETECTED");
    if (joined.contains("safety")) return List.of("SAFETY_POLICY_VIOLATION");
    if (joined.contains("evidence")) return List.of("EVIDENCE_INVALID");
    if (joined.contains("unsupported contract")) return List.of("UNSUPPORTED_CONTRACT_VERSION");
    return List.of("SCHEMA_INVALID");
  }
}
