package com.idfc.ai.runbook.rendering;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfc.ai.runbook.diff.OperationalDiff;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupplementalArtifactRendererTest {
  @Test
  void renders_deterministic_catalogs_and_no_change_release_impact() throws Exception {
    var data = new ObjectMapper().readTree("{\"generator\":{\"scanStatus\":\"COMPLETE\"},\"configuration\":[{\"propertyKey\":\"client.timeout\",\"configKey\":\"CLIENT_TIMEOUT\",\"repositoryDefault\":\"10000\",\"runtimeValueStatus\":\"CHECK_CONFIG_PORTAL\",\"logicalPurpose\":\"HTTP timeout\"}],\"apis\":[{\"path\":\"/health\",\"method\":\"GET\"}]}");
    Map<String, String> rendered = new SupplementalArtifactRenderer().render(data, new OperationalDiff(false, List.of(), Map.of()));
    assertThat(rendered.keySet()).containsExactly("CONFIGURATION-CATALOG.md", "API-CATALOG.md", "BUSINESS-RULES.md", "OBSERVABILITY-CATALOG.md", "ARCHITECTURE.md", "RELEASE-IMPACT.md");
    assertThat(rendered.get("CONFIGURATION-CATALOG.md")).contains("client.timeout", "CLIENT_TIMEOUT", "CHECK_CONFIG_PORTAL");
    assertThat(rendered.get("RELEASE-IMPACT.md")).contains("No operational changes detected.");
    assertThat(new SupplementalArtifactRenderer().render(data, new OperationalDiff(false, List.of(), Map.of()))).isEqualTo(rendered);
  }
}
