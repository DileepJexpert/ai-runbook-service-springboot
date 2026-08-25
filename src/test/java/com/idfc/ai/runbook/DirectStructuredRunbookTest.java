package com.idfc.ai.runbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfc.ai.runbook.agent.DirectStructuredRunbookPromptBuilder;
import com.idfc.ai.runbook.agent.RunbookPromptBuilder;
import com.idfc.ai.runbook.client.FakeRunbookAiClient;
import com.idfc.ai.runbook.config.RunbookProperties;
import com.idfc.ai.runbook.orchestration.RunbookJobService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectStructuredRunbookTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final RunbookProperties properties = new RunbookProperties();
  private final RunbookPromptBuilder promptBuilder = new RunbookPromptBuilder(properties);
  private final DirectStructuredRunbookPromptBuilder directPromptBuilder = new DirectStructuredRunbookPromptBuilder(promptBuilder, properties);

  @Test
  void prompt_builder_contains_canonical_templates_metadata_and_safety_rules() {
    String prompt = directPromptBuilder.buildPrompt(
        "payments-service",
        "BITBUCKET",
        "https://bitbucket.bank.local/scm/pay/payments.git",
        "develop",
        "develop-head-sha-9999",
        "FILE: pom.xml\n<project/>"
    );

    assertThat(prompt).contains("payments-service", "develop-head-sha-9999", "https://bitbucket.bank.local/scm/pay/payments.git");
    assertThat(prompt).contains("\"runbookData\"", "\"runbookEvidence\"", "\"securityFindings\"");
    assertThat(prompt).contains("contractVersion", "schemaVersion", "2.1");
    assertThat(prompt).contains("service.name", "scanStatus");
    assertThat(prompt).contains("READ-ONLY", "Manually mutate, insert, update, or delete production database");
    assertThat(prompt).contains("FILE: pom.xml");
  }

  @Test
  void envelope_parser_extracts_three_canonical_documents() {
    RunbookJobService service = new RunbookJobService(
        null, null, null, promptBuilder, null, null, null, null, null,
        null, null, null, null, null, null, null, mapper, properties,
        null, null, directPromptBuilder, null, null, null
    );

    var result = service.parseStructuredEnvelope(
        FakeRunbookAiClient.VALID_STRUCTURED_ENVELOPE_JSON,
        "payments-service",
        "develop-head-sha-9999"
    );

    assertThat(result.runbookData()).isNotNull();
    assertThat(result.runbookData().path("service").path("name").asText()).isEqualTo("payments-service");
    assertThat(result.runbookEvidence()).isNotNull();
    assertThat(result.runbookEvidence().path("facts")).isNotEmpty();
    assertThat(result.securityFindings()).isNotNull();
  }

  @Test
  void envelope_parser_handles_markdown_code_fences_cleanly() {
    RunbookJobService service = new RunbookJobService(
        null, null, null, promptBuilder, null, null, null, null, null,
        null, null, null, null, null, null, null, mapper, properties,
        null, null, directPromptBuilder, null, null, null
    );

    String fencedJson = "```json\n" + FakeRunbookAiClient.VALID_STRUCTURED_ENVELOPE_JSON + "\n```";

    var result = service.parseStructuredEnvelope(fencedJson, "payments-service", "develop-head-sha-9999");
    assertThat(result.runbookData()).isNotNull();
    assertThat(result.runbookEvidence()).isNotNull();
    assertThat(result.securityFindings()).isNotNull();
  }

  @Test
  void envelope_parser_fails_when_malformed_or_missing_keys() {
    RunbookJobService service = new RunbookJobService(
        null, null, null, promptBuilder, null, null, null, null, null,
        null, null, null, null, null, null, null, mapper, properties,
        null, null, directPromptBuilder, null, null, null
    );

    // Empty response
    assertThatThrownBy(() -> service.parseStructuredEnvelope("", "payments-service", "sha"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RUNBOOK_AI_OUTPUT_INVALID");

    // Malformed JSON
    assertThatThrownBy(() -> service.parseStructuredEnvelope("{invalid-json", "payments-service", "sha"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RUNBOOK_AI_OUTPUT_INVALID");

    // Missing runbookData
    assertThatThrownBy(() -> service.parseStructuredEnvelope("{\"runbookEvidence\":{}, \"securityFindings\":{}}", "payments-service", "sha"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RUNBOOK_AI_OUTPUT_INVALID")
        .hasMessageContaining("runbookData");

    // Missing runbookEvidence
    assertThatThrownBy(() -> service.parseStructuredEnvelope("{\"runbookData\":{}, \"securityFindings\":{}}", "payments-service", "sha"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RUNBOOK_AI_OUTPUT_INVALID")
        .hasMessageContaining("runbookEvidence");

    // Missing securityFindings
    assertThatThrownBy(() -> service.parseStructuredEnvelope("{\"runbookData\":{}, \"runbookEvidence\":{}}", "payments-service", "sha"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RUNBOOK_AI_OUTPUT_INVALID")
        .hasMessageContaining("securityFindings");
  }
}
