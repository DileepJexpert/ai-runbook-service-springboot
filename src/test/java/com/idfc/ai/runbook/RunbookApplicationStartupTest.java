package com.idfc.ai.runbook;

import com.idfc.ai.runbook.client.HttpRunbookAiClient;
import com.idfc.ai.runbook.client.RunbookAiClient;
import com.idfc.ai.runbook.orchestration.RunbookJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class RunbookApplicationStartupTest {

  @Autowired
  private RunbookAiClient runbookAiClient;

  @Autowired
  private RunbookJobService runbookJobService;

  @Test
  void local_profile_boots_cleanly_with_http_runbook_ai_client() {
    assertThat(runbookAiClient).isNotNull();
    assertThat(runbookAiClient).isInstanceOf(HttpRunbookAiClient.class);
    assertThat(runbookJobService).isNotNull();
  }
}
