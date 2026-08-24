package com.idfc.ai.runbook.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityGateServiceTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final QualityGateService gates = new QualityGateService();

  @Test
  void partial_scan_is_renderable_but_not_publishable() throws Exception {
    QualityGateDecision decision = gates.evaluate(mapper.readTree("{\"contractVersion\":\"2.1\",\"generator\":{\"scanStatus\":\"PARTIAL\"}}"), List.of());
    assertThat(decision.outcome()).isEqualTo(QualityGateDecision.RENDER_ALLOWED_PUBLISH_BLOCKED);
    assertThat(decision.publishEligible()).isFalse();
    assertThat(decision.warnings()).containsExactly("PARTIAL_SCAN");
  }

  @Test
  void secret_value_is_a_deterministic_failure() throws Exception {
    QualityGateDecision decision = gates.evaluate(mapper.readTree("{\"contractVersion\":\"2.1\",\"generator\":{\"scanStatus\":\"COMPLETE\"},\"configuration\":[{\"sensitive\":true,\"repositoryValue\":\"do-not-leak\"}]}"), List.of());
    assertThat(decision.outcome()).isEqualTo(QualityGateDecision.FAILED);
    assertThat(decision.errors()).containsExactly("SECRET_VALUE_DETECTED");
  }

  @Test
  void classifies_invalid_evidence_without_overloading_schema_failure() throws Exception {
    QualityGateDecision decision = gates.evaluate(mapper.readTree("{\"contractVersion\":\"2.1\",\"generator\":{\"scanStatus\":\"COMPLETE\"}}"), List.of("invalid evidence schema"));
    assertThat(decision.outcome()).isEqualTo(QualityGateDecision.FAILED);
    assertThat(decision.errors()).containsExactly("EVIDENCE_INVALID");
  }
}
