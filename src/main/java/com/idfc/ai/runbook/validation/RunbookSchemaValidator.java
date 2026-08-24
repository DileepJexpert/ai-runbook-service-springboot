package com.idfc.ai.runbook.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class RunbookSchemaValidator {
  private final ObjectMapper mapper = new ObjectMapper();

  public ValidationResult validate(JsonNode data, JsonNode evidence) {
    List<String> e = new ArrayList<>();
    String contract = data.path("contractVersion").asText();
    String schema = data.path("schemaVersion").asText();
    if (!"2.0".equals(contract) && !"2.1".equals(contract)) e.add("unsupported contractVersion");
    if (!"2.0".equals(schema) && !"2.1".equals(schema)) e.add("unsupported schemaVersion");
    if (!data.path("generator").path("scanStatus").asText().matches("COMPLETE|PARTIAL")) e.add("invalid scanStatus");
    if (data.path("service").path("name").asText().isBlank()) e.add("service.name required");
    String evidenceSchema = evidence.path("schemaVersion").asText();
    if ((!"1.0".equals(evidenceSchema) && !"2.1".equals(evidenceSchema)) || !evidence.path("facts").isArray()) e.add("invalid evidence schema");
    return new ValidationResult(e.isEmpty(), e);
  }

  public JsonNode loadSchema(String schemaFileName) {
    try {
      String json = new ClassPathResource("runbook-spec/schema/" + schemaFileName).getContentAsString(StandardCharsets.UTF_8);
      return mapper.readTree(json);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load schema from classpath: runbook-spec/schema/" + schemaFileName, e);
    }
  }

  public JsonNode getRunbookDataSchema() {
    return loadSchema("runbook-data.schema.json");
  }

  public JsonNode getRunbookEvidenceSchema() {
    return loadSchema("runbook-evidence.schema.json");
  }

  public JsonNode getSecurityFindingsSchema() {
    return loadSchema("security-findings.schema.json");
  }

  public JsonNode getRunbookDeltaSchema() {
    return loadSchema("runbook-delta.schema.json");
  }

  public JsonNode getGenerationReportSchema() {
    return loadSchema("generation-report.schema.json");
  }
}
