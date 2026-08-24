package com.idfc.ai.runbook.diff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunbookDeltaArtifactTest {
  @Test
  void creates_a_deterministic_contract_21_delta_envelope() {
    var diff = new OperationalDiff(true, List.of("api", "kafka"), Map.of("api", "abc"));
    var delta = RunbookDeltaArtifact.create("payments:TEST", "payments:sha", diff);
    assertThat(delta).containsEntry("contractVersion", "2.1").containsEntry("hasOperationalChanges", true);
    assertThat(delta.get("changedSections")).isEqualTo(List.of("api", "kafka"));
    assertThat((List<?>) delta.get("changes")).hasSize(2);
    assertThat(RunbookDeltaArtifact.create("payments:TEST", "payments:sha", diff)).isEqualTo(delta);
  }
}
