package com.idfc.ai.runbook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfc.ai.runbook.agent.RunbookPromptBuilder;
import com.idfc.ai.runbook.api.dto.CreateJobRequest;
import com.idfc.ai.runbook.config.RunbookProperties;
import com.idfc.ai.runbook.diff.OperationalDiff;
import com.idfc.ai.runbook.diff.RunbookComparator;
import com.idfc.ai.runbook.diff.SectionFingerprintService;
import com.idfc.ai.runbook.normalization.RunbookNormalizer;
import com.idfc.ai.runbook.normalization.StableIdService;
import com.idfc.ai.runbook.rendering.ConfluenceRunbookRenderer;
import com.idfc.ai.runbook.rendering.MarkdownRunbookRenderer;
import com.idfc.ai.runbook.rendering.RunbookSection;
import com.idfc.ai.runbook.rendering.SupplementalArtifactRenderer;
import com.idfc.ai.runbook.repository.LocalRepositoryWorkspaceProvider;
import com.idfc.ai.runbook.repository.RemoteGitResolver;
import com.idfc.ai.runbook.repository.RepositoryWorkspace;
import com.idfc.ai.runbook.validation.RunbookEvidenceValidator;
import com.idfc.ai.runbook.validation.RunbookSafetyValidator;
import com.idfc.ai.runbook.validation.RunbookSchemaValidator;
import com.idfc.ai.runbook.validation.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RunbookCoreTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void renders_the_fixed_support_document_contract_deterministically() throws Exception {
    JsonNode data = mapper.readTree(getClass().getResourceAsStream("/fixtures/runbook-data.json"));
    JsonNode normalized = new RunbookNormalizer(mapper).normalize(data);

    assertThat(normalized.at("/apis/0/timeoutMs").asInt()).isEqualTo(5000);

    MarkdownRunbookRenderer markdownRenderer = new MarkdownRunbookRenderer();
    ConfluenceRunbookRenderer confluenceRenderer = new ConfluenceRunbookRenderer();
    String markdown = markdownRenderer.render(normalized);
    String html = confluenceRenderer.render(normalized);

    for (RunbookSection section : RunbookSection.values()) {
      assertThat(markdown).contains("## " + section.heading);
      assertThat(html).contains("<h2>" + escapeHtml(section.heading) + "</h2>");
    }
    assertThat(markdown).contains(
        "| Endpoint/Event | Method/Direction | Authentication / Scope | Processing Model | Validation Summary | Success Meaning |",
        "| Table | Access | Lookup Key | Support-Relevant Fields | Operational Purpose |",
        "| Dependency | Purpose | Timeout | Automatic Failure Handling | Failure Propagation | Support Check |");
    assertThat(markdown).doesNotContain("{\"", "[{\"");
    assertThat(html).contains("<table>").doesNotContain("<pre>", "{&quot;", "[{&quot;");

    assertThat(markdownRenderer.render(normalized)).isEqualTo(markdown);
    assertThat(confluenceRenderer.render(normalized)).isEqualTo(html);
    assertThat(new RunbookComparator(new SectionFingerprintService(mapper)).compare(normalized, normalized)
        .hasOperationalChanges()).isFalse();
  }

  @Test
  void renders_absence_semantics_labels_and_business_flows_intentionally() throws Exception {
    JsonNode partial = mapper.readTree("""
        {"generator":{"scanStatus":"PARTIAL"},"service":{"name":"demo","businessOwner":"Ops","businessPurpose":"Support","criticality":"HIGH","escalationChannel":"#ops","supportOwner":"Platform"},"apis":[{"path":"/demo","method":"GET","authentication":"NOT_APPLICABLE"}],"businessFlows":[{"outcome":"Processes operational signals.","trigger":"Scheduled ingestion"}],"databaseTables":[{"schema":"public","table":"incidents","access":"READ_WRITE"}],"downstreamDependencies":[{"name":"ledger","purpose":"Records entries."}],"kafkaConsumers":[{"topic":"events","group":"demo"}]}
        """);
    String partialMarkdown = new MarkdownRunbookRenderer().render(partial);
    String partialHtml = new ConfluenceRunbookRenderer().render(partial);

    assertThat(partialMarkdown).contains("| /demo | GET | Not Applicable | Could not be confirmed from repository | Could not be confirmed from repository | Could not be confirmed from repository |");
    assertThat(partialMarkdown).contains("| `public.incidents` | `READ_WRITE` | Could not be confirmed from repository | Could not be confirmed from repository | Could not be confirmed from repository |");
    assertThat(partialMarkdown).contains("**Business Owner:** Ops", "**Business Purpose:** Support", "**Escalation Channel:** #ops", "**Support Owner:** Platform");
    assertThat(partialMarkdown).contains("**Trigger:** Scheduled ingestion\n\n**Outcome:** Processes operational signals.").doesNotContain("- outcome:", "- trigger:");
    assertThat(partialHtml).contains("<strong>Trigger:</strong> Scheduled ingestion", "<strong>Outcome:</strong> Processes operational signals.");

    JsonNode complete = mapper.readTree("{" + "\"generator\":{\"scanStatus\":\"COMPLETE\"},\"service\":{\"name\":\"demo\"},\"apis\":[{\"path\":\"/demo\",\"method\":\"GET\"}]}" );
    assertThat(new MarkdownRunbookRenderer().render(complete)).contains("| /demo | GET | Not found in repository | Not found in repository | Not found in repository | Not found in repository |");
    assertThat(new MarkdownRunbookRenderer().render(partial)).isEqualTo(partialMarkdown);
  }

  @Test
  void renders_all_fixed_tables_with_valid_shape_identifiers_and_clean_ending() throws Exception {
    JsonNode data = mapper.readTree("""
        {"generator":{"scanStatus":"COMPLETE"},"service":{"name":"tables"},"pipeline":{"serviceName":"tables","environment":"LOCAL"},"apis":[{"path":"/api","method":"GET","authentication":"scope","processingModel":"sync","validationSummary":"validated","successMeaning":"ok"}],"businessRules":[{"condition":"valid","dataUsed":"request","result":"allow","supportCheck":"logs"}],"responseMappings":[{"errorCode":"E1","result":"fail","possibleCauses":"input","howToConfirm":"logs","supportAction":"escalate"}],"downstreamDependencies":[{"name":"ledger","purpose":"record","timeout":"2s","failureHandling":"retry","failurePropagation":"fail","supportCheck":"check"}],"databaseTables":[{"schema":"public","table":"remediation_log","access":"READ_WRITE","lookupKey":"id","supportFields":"status","operationalPurpose":"audit"}],"kafkaConsumers":[{"topic":"events","direction":"INBOUND","group":"group","businessPurpose":"consume","failureHandling":"retry"}],"retentionRules":[{"store":"audit","retention":"30d","mechanism":"ttl","archiveDestination":"archive","supportImpact":"none"}],"migrationRisks":[{"migration":"V1","changeType":"schema","compatibilityRisk":"COULD_NOT_BE_DETERMINED","supportMeaning":"review"}]}
        """);
    MarkdownRunbookRenderer markdownRenderer = new MarkdownRunbookRenderer();
    ConfluenceRunbookRenderer confluenceRenderer = new ConfluenceRunbookRenderer();
    String markdown = markdownRenderer.render(data);
    String html = confluenceRenderer.render(data);

    String[][] tables = {
        {"| Endpoint/Event | Method/Direction | Authentication / Scope | Processing Model | Validation Summary | Success Meaning |", "6"},
        {"| Business Condition | Data Used | Result / Response | Support Check |", "4"},
        {"| Error Code / Log Signature | Result | Possible Causes | How to Confirm | Support Action |", "5"},
        {"| Dependency | Purpose | Timeout | Automatic Failure Handling | Failure Propagation | Support Check |", "6"},
        {"| Table | Access | Lookup Key | Support-Relevant Fields | Operational Purpose |", "5"},
        {"| Topic | Direction | Consumer Group | Business Purpose | Failure Handling |", "5"},
        {"| Data Store / Record | Retention / Expiry | Mechanism | Archive Destination | Support Impact |", "5"},
        {"| Migration | Change Type | Compatibility Risk | Support Meaning |", "4"}
    };
    for (String[] table : tables) {
      assertThat(markdown).contains(table[0]);
      assertMarkdownTableShape(markdown, table[0], Integer.parseInt(table[1]));
      assertThat(htmlHeader(markdownHeaderToHtml(table[0]), html)).isEqualTo(Integer.parseInt(table[1]));
    }
    assertThat(markdown).contains("| -------------- | ---------------- | ---------------------- | ---------------- | ------------------ | --------------- |");
    assertThat(markdown).doesNotContain("Endpoint/EventMethod/Direction", "DependencyPurposeTimeout", "TableAccessLookup", "TopicDirectionConsumer", "MigrationChange Type");
    assertThat(markdown).contains("`READ_WRITE`", "`public.remediation_log`", "`COULD_NOT_BE_DETERMINED`");
    assertThat(html).contains("<code>READ_WRITE</code>", "<code>public.remediation_log</code>", "<code>COULD_NOT_BE_DETERMINED</code>");
    assertThat(markdown).doesNotMatch("(?m)^-\\s*$").endsWith("**Environment:** LOCAL\n\n");
    assertThat(html).doesNotContain("<li></li>", "<p>Not Applicable</p><!-- MANUAL SUPPORT NOTES");
    assertThat(markdownRenderer.render(data)).isEqualTo(markdown);
    assertThat(confluenceRenderer.render(data)).isEqualTo(html);
  }

  @Test
  void renders_confluence_object_arrays_as_single_level_lists_without_changing_markdown() throws Exception {
    JsonNode data = mapper.readTree("""
        {"generator":{"scanStatus":"COMPLETE"},"service":{"name":"lists"},"resilience":[{"behavior":"Uses fallback."}],"unknownIncidentContext":[{"guidance":"Collect evidence."}],"escalationEvidence":[{"item":"Capture logs."}]}
        """);
    String html = new ConfluenceRunbookRenderer().render(data);
    String markdown = new MarkdownRunbookRenderer().render(data);

    assertThat(html).doesNotContain("<ul><li><ul><li>");
    assertThat(html).contains("<ul><li><strong>Behavior:</strong> Uses fallback.</li></ul>", "<ul><li><strong>Guidance:</strong> Collect evidence.</li></ul>", "<ul><li><strong>Item:</strong> Capture logs.</li></ul>");
    assertThat(markdown).contains("- **Behavior:** Uses fallback.", "- **Guidance:** Collect evidence.", "- **Item:** Capture logs.");
  }

  @Test
  void renders_present_platform_canonical_mappings_without_replacing_them_as_unknown() throws Exception {
    JsonNode data = mapper.readTree("""
        {"generator":{"scanStatus":"PARTIAL"},"service":{"name":"idfc-integration-platform"},"downstreamDependencies":[{"name":"Journey registry","purpose":"Journey source","timeout":"connect 3000 ms; read 10000 ms"},{"name":"IMPS vendor","purpose":"Transfer","timeout":"connect 3000 ms; read 10000 ms"}],"aerospike":[{"namespace":"idfc","set":"idem","access":"READ_WRITE","operationalPurpose":"Idempotency"},{"namespace":"idfc","set":"idem_app","access":"READ_WRITE","operationalPurpose":"Application pointer"}],"capacityConstraints":[{"constraint":"FinnOne maximum concurrency = 4"},{"constraint":"Communications max concurrency default = 4"}],"supportHealthChecks":[{"check":"Actuator health/info/prometheus exposure and health probes."}],"featureFlags":[{"property":"IDFC_ENGINE_JOURNEY_SOURCE","default":"classpath","purpose":"Journey source"}],"kafkaConsumers":[{"topic":"orig.sfdc.pl.v1","consumerGroup":"engine","businessPurpose":"Start journey"}],"kafkaProducers":[{"topic":"orig.sfdc.dlq.v1","businessPurpose":"DLQ"}]}
        """);
    String markdown = new MarkdownRunbookRenderer().render(data);
    String html = new ConfluenceRunbookRenderer().render(data);

    assertThat(markdown).contains("connect 3000 ms; read 10000 ms", "`idfc.idem`", "`idfc.idem_app`", "FinnOne maximum concurrency = 4", "Communications max concurrency default = 4", "Actuator health/info/prometheus exposure and health probes.", "IDFC_ENGINE_JOURNEY_SOURCE", "orig.sfdc.dlq.v1", "CONSUMER / INBOUND", "PRODUCER / OUTBOUND");
    assertThat(html).contains("connect 3000 ms; read 10000 ms", "<code>idfc.idem</code>", "<code>idfc.idem_app</code>", "FinnOne maximum concurrency = 4", "Communications max concurrency default = 4", "Actuator health/info/prometheus exposure and health probes.", "IDFC_ENGINE_JOURNEY_SOURCE", "orig.sfdc.dlq.v1", "CONSUMER / INBOUND", "PRODUCER / OUTBOUND");
    assertThat(markdown).doesNotContain("| Journey registry | Journey source | Could not be confirmed from repository", "| IMPS vendor | Transfer | Could not be confirmed from repository");
  }

  @Test
  void normalizes_multimodule_scoped_configuration_without_collision() throws Exception {
    JsonNode data = mapper.readTree("""
        {
          "generator":{"scanStatus":"COMPLETE"},
          "service":{"name":"multimodule-test"},
          "configuration":[
            {"componentId":"capabilities/bureau","propertyKey":"server.port","configKey":"SERVER_PORT","repositoryDefault":"8092","logicalPurpose":"HTTP port","runtimeValueStatus":"CHECK_CONFIG_PORTAL"},
            {"componentId":"edges/sfdc-ingress-edge","propertyKey":"server.port","configKey":"SERVER_PORT","repositoryDefault":"8080","logicalPurpose":"HTTP port","runtimeValueStatus":"CHECK_CONFIG_PORTAL"},
            {"componentId":"capabilities/bureau","propertyKey":"spring.kafka.bootstrap-servers","configKey":"KAFKA_BOOTSTRAP_SERVERS","repositoryDefault":"localhost:9092","logicalPurpose":"Kafka broker","runtimeValueStatus":"CHECK_CONFIG_PORTAL"},
            {"componentId":"edges/sfdc-ingress-edge","propertyKey":"spring.kafka.bootstrap-servers","configKey":"KAFKA_BOOTSTRAP_SERVERS","repositoryDefault":"localhost:9092","logicalPurpose":"Kafka broker","runtimeValueStatus":"CHECK_CONFIG_PORTAL"}
          ]
        }
        """);

    JsonNode normalized = new RunbookNormalizer(mapper).normalize(data);
    JsonNode config = normalized.path("configuration");
    assertThat(config.size()).isEqualTo(4);

    assertThat(config.get(0).path("id").asText()).isEqualTo("CONFIG:capabilities/bureau:server.port");
    assertThat(config.get(0).path("repositoryDefault").asText()).isEqualTo("8092");

    assertThat(config.get(1).path("id").asText()).isEqualTo("CONFIG:capabilities/bureau:spring.kafka.bootstrap-servers");
    assertThat(config.get(1).path("repositoryDefault").asText()).isEqualTo("localhost:9092");

    assertThat(config.get(2).path("id").asText()).isEqualTo("CONFIG:edges/sfdc-ingress-edge:server.port");
    assertThat(config.get(2).path("repositoryDefault").asText()).isEqualTo("8080");

    assertThat(config.get(3).path("id").asText()).isEqualTo("CONFIG:edges/sfdc-ingress-edge:spring.kafka.bootstrap-servers");
    assertThat(config.get(3).path("repositoryDefault").asText()).isEqualTo("localhost:9092");

    // Test Configuration Catalog rendering shows Component column
    SupplementalArtifactRenderer renderer = new SupplementalArtifactRenderer();
    Map<String, String> artifacts = renderer.render(normalized, new OperationalDiff(false, List.of(), Map.of()));
    String catalog = artifacts.get("CONFIGURATION-CATALOG.md");
    assertThat(catalog).contains("| Component | Purpose | Property Key | Config Portal Key | Repository Value | Repository Default | Runtime Value Status | Sensitive | Support Lookup |");
    assertThat(catalog).contains("capabilities/bureau", "edges/sfdc-ingress-edge");
  }

  @Test
  void evidence_validator_rejects_generated_build_directories() throws Exception {
    JsonNode badEvidence = mapper.readTree("""
        {
          "schemaVersion": "2.1",
          "facts": [
            {
              "factId": "CONFIG:edges/sfdc-ingress-edge:server.port",
              "factType": "configuration",
              "fact": "Port config",
              "confidence": "HIGH",
              "sourceEvidence": [
                {"file": "edges/sfdc-ingress-edge/build/resources/main/application.yml", "lineStart": 1, "lineEnd": 2}
              ]
            }
          ]
        }
        """);

    RunbookEvidenceValidator validator = new RunbookEvidenceValidator();
    ValidationResult badResult = validator.validate(badEvidence);
    assertThat(badResult.valid()).isFalse();
    assertThat(badResult.errors()).anyMatch(err -> err.contains("evidence cites generated build directory"));

    JsonNode goodEvidence = mapper.readTree("""
        {
          "schemaVersion": "2.1",
          "facts": [
            {
              "factId": "CONFIG:edges/sfdc-ingress-edge:server.port",
              "factType": "configuration",
              "fact": "Port config",
              "confidence": "HIGH",
              "sourceEvidence": [
                {"file": "edges/sfdc-ingress-edge/src/main/resources/application.yml", "lineStart": 1, "lineEnd": 2}
              ]
            }
          ]
        }
        """);

    ValidationResult goodResult = validator.validate(goodEvidence);
    assertThat(goodResult.valid()).isTrue();
  }

  @Test
  void downstream_dependencies_table_renders_automatic_failure_handling_in_markdown_and_html() throws Exception {
    JsonNode data = mapper.readTree("""
        {
          "generator": {"scanStatus": "COMPLETE"},
          "service": {"name": "pravah-app"},
          "downstreamDependencies": [
            {
              "name": "CBS",
              "purpose": "Executes payer account debits.",
              "timeout": "5s",
              "automaticFailureHandling": "Network exceptions return UNKNOWN outcome; business declines trigger immediate DECLINED state; definite credit failures trigger automated reversals.",
              "failurePropagation": "HTTP 502 Bad Gateway",
              "supportCheck": "Verify CBS account balance and logs."
            },
            {
              "name": "Rail",
              "purpose": "Executes beneficiary account credits.",
              "timeout": "10s",
              "automaticFailureHandling": "Timeouts/exceptions mark payment DEEMED; definite failures trigger automatic CBS reversal.",
              "failurePropagation": "Async reconciliation",
              "supportCheck": "Enquire rail status using stable credit reference."
            },
            {
              "name": "AuditStore",
              "purpose": "Stores audit events.",
              "supportCheck": "Check audit DB."
            }
          ]
        }
        """);

    String markdown = new MarkdownRunbookRenderer().render(data);
    String html = new ConfluenceRunbookRenderer().render(data);

    // 1. automaticFailureHandling present appears exactly in RUNBOOK.md
    assertThat(markdown).contains("Network exceptions return UNKNOWN outcome; business declines trigger immediate DECLINED state; definite credit failures trigger automated reversals.");
    assertThat(markdown).contains("Timeouts/exceptions mark payment DEEMED; definite failures trigger automatic CBS reversal.");

    // 2. automaticFailureHandling present appears in confluence-body.html
    assertThat(html).contains("Network exceptions return UNKNOWN outcome; business declines trigger immediate DECLINED state; definite credit failures trigger automated reversals.");
    assertThat(html).contains("Timeouts/exceptions mark payment DEEMED; definite failures trigger automatic CBS reversal.");

    // 3. missing automaticFailureHandling renders normal repository-unknown value
    assertThat(markdown).contains("| AuditStore | Stores audit events. | Not found in repository | Not found in repository | Not found in repository | Check audit DB. |");
    assertThat(html).contains("<td>AuditStore</td><td>Stores audit events.</td><td>Not found in repository</td><td>Not found in repository</td><td>Not found in repository</td><td>Check audit DB.</td>");

    // 4. CBS and Rail have different automaticFailureHandling values without collapsing
    assertThat(markdown).contains("| CBS | Executes payer account debits. | 5s | Network exceptions return UNKNOWN outcome; business declines trigger immediate DECLINED state; definite credit failures trigger automated reversals. | HTTP 502 Bad Gateway | Verify CBS account balance and logs. |");
    assertThat(markdown).contains("| Rail | Executes beneficiary account credits. | 10s | Timeouts/exceptions mark payment DEEMED; definite failures trigger automatic CBS reversal. | Async reconciliation | Enquire rail status using stable credit reference. |");
  }

  @Test
  void safety_validator_allows_explicit_safe_prohibitions() throws Exception {
    RunbookSafetyValidator validator = new RunbookSafetyValidator();

    String[] safePhrases = {
        "Do not manually update database rows.",
        "Never replay source Kafka events.",
        "L1/L2 must not change offsets.",
        "Support should not modify production configuration.",
        "Do not update database rows.",
        "Never replay Kafka events.",
        "Do not manually reprocess the payment.",
        "Do not force schedulers.",
        "Do not initiate manual reconciliation.",
        "Support must not initiate reconciliation.",
        "Never trigger manual reconciliation.",
        "Collect service name, correlationId, and non-sensitive error evidence. Do not replay messages, alter offsets, mutate datastore records, or force schedulers."
    };

    for (String phrase : safePhrases) {
      JsonNode data = mapper.readTree(String.format("""
          {
            "contractVersion": "2.1",
            "unknownIncidentContext": [{"guidance": "%s"}],
            "businessRules": [{"condition": "test", "supportCheck": "%s"}]
          }
          """, phrase, phrase));
      ValidationResult result = validator.validate(data);
      assertThat(result.valid()).withFailMessage("Expected safe phrase to pass: " + phrase).isTrue();
      assertThat(result.errors()).isEmpty();
    }
  }

  @Test
  void safety_validator_rejects_affirmative_unsafe_support_instructions() throws Exception {
    RunbookSafetyValidator validator = new RunbookSafetyValidator();

    record UnsafeCase(String phrase, String expectedRule) {}
    List<UnsafeCase> cases = List.of(
        new UnsafeCase("Support should update the database record.", "NO_MANUAL_DATABASE_MUTATION"),
        new UnsafeCase("L1/L2 can replay the Kafka event.", "NO_EVENT_REPLAY"),
        new UnsafeCase("Reprocess the payment manually.", "NO_MANUAL_REPROCESSING"),
        new UnsafeCase("Change the Kafka consumer offset.", "NO_OFFSET_MUTATION"),
        new UnsafeCase("Change the consumer offset.", "NO_OFFSET_MUTATION"),
        new UnsafeCase("Trigger manual reconciliation for this payment.", "NO_MANUAL_STATE_RECONCILIATION"),
        new UnsafeCase("Initiate manual reconciliation.", "NO_MANUAL_STATE_RECONCILIATION"),
        new UnsafeCase("Check the state and initiate enquiry or manual reconciliation.", "NO_MANUAL_STATE_RECONCILIATION"),
        new UnsafeCase("Check payment current_state and initiate enquiry or manual reconciliation", "NO_MANUAL_STATE_RECONCILIATION"),
        new UnsafeCase("Support can initiate reconciliation.", "NO_MANUAL_STATE_RECONCILIATION"),
        new UnsafeCase("Trigger reconciliation for this payment.", "NO_MANUAL_STATE_RECONCILIATION"),
        new UnsafeCase("Run manual reconciliation.", "NO_MANUAL_STATE_RECONCILIATION"),
        new UnsafeCase("Modify production configuration.", "NO_PROD_CONFIG_MUTATION"),
        new UnsafeCase("Restart or scale the service as a recovery action.", "NO_RESTART_SCALE_INSTRUCTION"),
        new UnsafeCase("Trigger state-changing reconciliation manually.", "NO_MANUAL_STATE_RECONCILIATION")
    );

    for (UnsafeCase testCase : cases) {
      JsonNode data = mapper.readTree(String.format("""
          {
            "contractVersion": "2.1",
            "unknownIncidentContext": [{"guidance": "%s"}]
          }
          """, testCase.phrase()));
      ValidationResult result = validator.validate(data);
      assertThat(result.valid()).withFailMessage("Expected unsafe phrase to fail: " + testCase.phrase()).isFalse();
      assertThat(result.errors()).hasSize(1);
      String error = result.errors().get(0);
      assertThat(error).contains("[SAFETY_POLICY_VIOLATION]");
      assertThat(error).contains("$.unknownIncidentContext[0].guidance");
      assertThat(error).contains(testCase.expectedRule());
    }
  }

  @Test
  void safety_validator_allows_implementation_descriptions_with_database_or_retries() throws Exception {
    RunbookSafetyValidator validator = new RunbookSafetyValidator();

    JsonNode data = mapper.readTree("""
        {
          "contractVersion": "2.1",
          "processingSummary": {"summary": "Outbox relay worker automatically updates database table status and retries publishing."},
          "databaseTables": [{"table": "payment", "operationalPurpose": "System updates database records during payment execution."}],
          "resilience": [{"behavior": "Retries Kafka event publishing on network failure."}]
        }
        """);

    ValidationResult result = validator.validate(data);
    assertThat(result.valid()).isTrue();
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void safety_validator_detects_secrets_with_deterministic_codes_and_no_leakage() throws Exception {
    RunbookSafetyValidator validator = new RunbookSafetyValidator();

    JsonNode data = mapper.readTree("""
        {
          "contractVersion": "2.1",
          "configuration": [
            {"propertyKey": "app.token", "logicalPurpose": "token", "runtimeValueStatus": "KNOWN_FROM_REPOSITORY", "repositoryValue": "bearer secret-token-123456789"},
            {"propertyKey": "app.pwd", "logicalPurpose": "db", "runtimeValueStatus": "KNOWN_FROM_REPOSITORY", "repositoryValue": "password=super-secret-pass"}
          ]
        }
        """);

    ValidationResult result = validator.validate(data);
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).hasSize(2);

    for (String error : result.errors()) {
      assertThat(error).contains("[SECRET_VALUE_DETECTED]");
      assertThat(error).doesNotContain("secret-token-123456789");
      assertThat(error).doesNotContain("super-secret-pass");
    }
  }

  @Test
  void self_contained_spec_loads_completely_from_classpath() throws Exception {
    RunbookProperties properties = new RunbookProperties();
    RunbookPromptBuilder builder = new RunbookPromptBuilder(properties);

    // A. extraction contract loads from classpath
    String contract = builder.extractionContract();
    assertThat(contract).isNotBlank().contains("Extraction Contract 2.1", "Service Intelligence Extraction Contract 2.1");

    // B. safety policy loads from classpath
    String safety = builder.safetyPolicy();
    assertThat(safety).isNotBlank().contains("Safety Policy 2.1", "Production Support Read-Only Rule");

    // C. platform context loads from classpath
    String context = builder.context();
    assertThat(context).isNotBlank().contains("Standard Platform Context", "Production Support Boundaries");
    assertThat(context).doesNotContain("unless a separately governed procedure authorizes it");

    // D. prompt v3 loads from classpath
    String promptV3 = builder.promptV3();
    assertThat(promptV3).isNotBlank().contains("Service Intelligence extraction prompt 2.1", "runbook-data.json", "runbook-evidence.json", "security-findings.json");

    // E. schemas load from classpath
    RunbookSchemaValidator schemaValidator = new RunbookSchemaValidator();
    JsonNode dataSchema = schemaValidator.getRunbookDataSchema();
    assertThat(dataSchema.path("$schema").asText()).isNotBlank();

    JsonNode evidenceSchema = schemaValidator.getRunbookEvidenceSchema();
    assertThat(evidenceSchema.path("$schema").asText()).isNotBlank();

    JsonNode secSchema = schemaValidator.getSecurityFindingsSchema();
    assertThat(secSchema.path("$schema").asText()).isNotBlank();

    JsonNode deltaSchema = schemaValidator.getRunbookDeltaSchema();
    assertThat(deltaSchema.path("$schema").asText()).isNotBlank();

    JsonNode reportSchema = schemaValidator.getGenerationReportSchema();
    assertThat(reportSchema.path("$schema").asText()).isNotBlank();

    // F. assembled prompt contains contract 2.1
    String assembledPrompt = builder.prompt();
    assertThat(assembledPrompt).contains("2.1", "Service Intelligence");

    // G. assembled prompt contains read-only Support safety rule
    assertThat(assembledPrompt).contains("READ-ONLY", "Production Support");
    assertThat(assembledPrompt).contains("NO_MANUAL_STATE_RECONCILIATION");

    // H. assembled prompt does not use legacy prompt v2
    assertThat(assembledPrompt).doesNotContain("[LEGACY - CONTRACT 2.0 ONLY / NOT USED BY CONTRACT 2.1]");

    // Other required spec components
    assertThat(builder.configExtractionPolicy()).contains("Configuration Extraction Contract 2.1");
    assertThat(builder.evidencePolicy()).contains("Evidence Policy 2.1");
    assertThat(builder.qualityExpectations()).contains("Extraction quality expectations 2.1");
    assertThat(builder.regulatoryEvidencePolicy()).contains("Regulatory evidence policy 2.1");

    // Explicit skeletons and canonical structure rules in active prompt
    assertThat(assembledPrompt).contains("\"contractVersion\": \"2.1\"", "\"schemaVersion\": \"2.1\"");
    assertThat(assembledPrompt).contains("\"generator\"", "\"scanStatus\": \"COMPLETE\"");
    assertThat(assembledPrompt).contains("\"service\":", "\"name\":");
    assertThat(assembledPrompt).contains("DO NOT place \"serviceName\" at the root level");
    assertThat(assembledPrompt).contains("DO NOT place \"scanStatus\" at the root level");
    assertThat(assembledPrompt).contains("DO NOT add \"contractVersion\", \"serviceName\", or \"scanStatus\" at the root of `runbook-evidence.json`");
  }

  @Test
  void bundled_templates_validate_against_schemas() throws Exception {
    RunbookSchemaValidator schemaValidator = new RunbookSchemaValidator();
    JsonNode dataTemplate = schemaValidator.getRunbookDataTemplate();
    JsonNode evidenceTemplate = schemaValidator.getRunbookEvidenceTemplate();
    JsonNode securityTemplate = schemaValidator.getSecurityFindingsTemplate();

    // 1. runbook-data template validates against schema
    assertThat(dataTemplate.path("contractVersion").asText()).isEqualTo("2.1");
    assertThat(dataTemplate.path("schemaVersion").asText()).isEqualTo("2.1");
    assertThat(dataTemplate.path("generator").path("scanStatus").asText()).isEqualTo("COMPLETE");
    assertThat(dataTemplate.path("service").path("name").asText()).isEqualTo("<SERVICE_NAME>");

    // 2. runbook-evidence template validates against schema
    assertThat(evidenceTemplate.path("schemaVersion").asText()).isEqualTo("2.1");
    assertThat(evidenceTemplate.path("facts").isArray()).isTrue();

    // 3. security-findings template validates against schema
    assertThat(securityTemplate.path("contractVersion").asText()).isEqualTo("2.1");
    assertThat(securityTemplate.path("findings").isArray()).isTrue();

    ValidationResult result = schemaValidator.validate(dataTemplate, evidenceTemplate);
    assertThat(result.valid()).isTrue();
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void canonical_sample_artifacts_validate_against_schemas() throws Exception {
    RunbookSchemaValidator schemaValidator = new RunbookSchemaValidator();
    ObjectMapper om = new ObjectMapper();

    JsonNode validData = om.readTree("""
        {
          "contractVersion": "2.1",
          "schemaVersion": "2.1",
          "generator": {
            "promptVersion": "2.1",
            "platformContextVersion": "2.1",
            "scanStatus": "COMPLETE",
            "unscannedAreas": []
          },
          "pipeline": {
            "serviceName": "payments-integration-services",
            "gitCommitSha": "6ed4594439c50e6943e5dff52fc53ac41dbc68c5"
          },
          "service": {
            "name": "payments-integration-services",
            "businessPurpose": "Processes payments",
            "criticality": "HIGH",
            "supportOwner": "Payments Team",
            "businessOwner": "Retail Banking",
            "escalationChannel": "#payments-support"
          },
          "processingSummary": {
            "summary": "Handles payment processing."
          }
        }
        """);

    JsonNode validEvidence = om.readTree("""
        {
          "schemaVersion": "2.1",
          "facts": [
            {
              "factId": "SVC:NAME",
              "factType": "SERVICE",
              "fact": "Service identity is payments-integration-services",
              "provenance": "CODE",
              "confidence": "HIGH",
              "sourceEvidence": [
                {
                  "file": "pom.xml",
                  "lineStart": 4,
                  "lineEnd": 4
                }
              ]
            }
          ]
        }
        """);

    ValidationResult result = schemaValidator.validate(validData, validEvidence);
    assertThat(result.valid()).isTrue();
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void invalid_flat_serviceName_and_missing_schemaVersion_fail_validation() throws Exception {
    RunbookSchemaValidator schemaValidator = new RunbookSchemaValidator();
    ObjectMapper om = new ObjectMapper();

    // AI output with flat serviceName, root scanStatus, and missing schemaVersion
    JsonNode invalidData = om.readTree("""
        {
          "contractVersion": "2.1",
          "serviceName": "payments-integration-services",
          "scanStatus": "COMPLETE"
        }
        """);

    JsonNode invalidEvidence = om.readTree("""
        {
          "contractVersion": "2.1",
          "serviceName": "payments-integration-services",
          "scanStatus": "COMPLETE"
        }
        """);

    ValidationResult result = schemaValidator.validate(invalidData, invalidEvidence);
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).contains("unsupported schemaVersion", "invalid scanStatus", "service.name required", "invalid evidence schema");
  }

  @Test
  void bitbucket_repository_request_parsing_and_branch_resolution() {
    RemoteGitResolver mockResolver = (url, branch) -> {
      if ("develop".equals(branch)) return "develop-sha-1234567890";
      if (branch == null || branch.isBlank()) return "main-sha-0987654321";
      throw new IllegalArgumentException("unknown branch: " + branch);
    };

    LocalRepositoryWorkspaceProvider provider = new LocalRepositoryWorkspaceProvider(mockResolver);

    // 1. Branch supplied
    CreateJobRequest.Repository repoWithBranch = new CreateJobRequest.Repository("BITBUCKET", null, "https://bitbucket.bank.local/scm/pay/payments.git", "develop", null);
    RepositoryWorkspace wsBranch = provider.prepare(repoWithBranch, null);
    assertThat(wsBranch.mode()).isEqualTo("BITBUCKET");
    assertThat(wsBranch.commitSha()).isEqualTo("develop-sha-1234567890");
    assertThat(wsBranch.url()).isEqualTo("https://bitbucket.bank.local/scm/pay/payments.git");
    assertThat(wsBranch.branch()).isEqualTo("develop");

    // 2. Branch omitted (resolves default remote HEAD)
    CreateJobRequest.Repository repoNoBranch = new CreateJobRequest.Repository("BITBUCKET", null, "https://bitbucket.bank.local/scm/pay/payments.git", null, null);
    RepositoryWorkspace wsDefault = provider.prepare(repoNoBranch, null);
    assertThat(wsDefault.mode()).isEqualTo("BITBUCKET");
    assertThat(wsDefault.commitSha()).isEqualTo("main-sha-0987654321");

    // 3. Exact commit supplied - takes precedence over remote HEAD
    CreateJobRequest.Repository repoExact = new CreateJobRequest.Repository("BITBUCKET", null, "https://bitbucket.bank.local/scm/pay/payments.git", "develop", "exact-sha-5555555");
    RepositoryWorkspace wsExact = provider.prepare(repoExact, "exact-sha-5555555");
    assertThat(wsExact.commitSha()).isEqualTo("exact-sha-5555555");
  }

  @Test
  void bitbucket_repository_validates_required_url_and_unsupported_modes() {
    RemoteGitResolver mockResolver = (url, branch) -> "dummy-sha";
    LocalRepositoryWorkspaceProvider provider = new LocalRepositoryWorkspaceProvider(mockResolver);

    // Missing URL in BITBUCKET mode
    CreateJobRequest.Repository missingUrl = new CreateJobRequest.Repository("BITBUCKET", null, "", null, null);
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> provider.prepare(missingUrl, null));

    // Missing localPath in LOCAL_PATH mode
    CreateJobRequest.Repository missingPath = new CreateJobRequest.Repository("LOCAL_PATH", "", null, null, null);
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> provider.prepare(missingPath, null));

    // Unsupported mode
    CreateJobRequest.Repository invalidMode = new CreateJobRequest.Repository("SVN", null, null, null, null);
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> provider.prepare(invalidMode, null));
  }

  private void assertMarkdownTableShape(String markdown, String header, int columns) {
    String[] lines = markdown.split("\\R");
    for (int index = 0; index < lines.length; index++) {
      if (lines[index].equals(header)) {
        assertThat(pipeCount(lines[index])).isEqualTo(columns + 1);
        assertThat(pipeCount(lines[index + 2])).isEqualTo(columns + 1);
        return;
      }
    }
    throw new AssertionError("Header not found: " + header);
  }

  private int htmlHeader(String expectedHeader, String html) {
    int start = html.indexOf(expectedHeader);
    assertThat(start).isGreaterThanOrEqualTo(0);
    int end = html.indexOf("</thead>", start);
    return (int) Pattern.compile("<th>").matcher(html.substring(start, end)).results().count();
  }

  private String markdownHeaderToHtml(String markdownHeader) {
    String[] columns = markdownHeader.substring(2, markdownHeader.length() - 2).split(" \\| ");
    StringBuilder header = new StringBuilder("<thead><tr>");
    for (String column : columns) header.append("<th>").append(column.replace("&", "&amp;")).append("</th>");
    return header.append("</tr>").toString();
  }

  private int pipeCount(String line) { return (int) line.chars().filter(character -> character == '|').count(); }

  private String escapeHtml(String value) {
    return value.replace("&", "&amp;");
  }
}
