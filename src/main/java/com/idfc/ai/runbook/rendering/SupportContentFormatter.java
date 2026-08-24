package com.idfc.ai.runbook.rendering;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

final class SupportContentFormatter {
  static final String NOT_APPLICABLE = "Not Applicable";
  static final String NOT_FOUND = "Not found in repository";
  static final String UNCONFIRMED = "Could not be confirmed from repository";
  record Table(String[] columns, String[][] fields) {}

  private static final Map<RunbookSection, Table> TABLES = Map.of(
      RunbookSection.API, new Table(new String[]{"Endpoint/Event", "Method/Direction", "Authentication / Scope", "Processing Model", "Validation Summary", "Success Meaning"}, new String[][]{{"path", "endpoint", "event"}, {"method", "direction"}, {"authentication", "scope"}, {"processingModel"}, {"validationSummary"}, {"successMeaning"}}),
      RunbookSection.RULES, new Table(new String[]{"Business Condition", "Data Used", "Result / Response", "Support Check"}, new String[][]{{"condition"}, {"dataUsed", "source"}, {"result", "response"}, {"supportCheck"}}),
      RunbookSection.RESPONSES, errorTable(),
      RunbookSection.DOWNSTREAM, new Table(new String[]{"Dependency", "Purpose", "Timeout", "Automatic Failure Handling", "Failure Propagation", "Support Check"}, new String[][]{{"name", "dependency"}, {"purpose"}, {"timeout", "timeoutMs"}, {"automaticFailureHandling", "failureHandling", "retry"}, {"failurePropagation"}, {"supportCheck"}}),
      RunbookSection.DATASTORE, new Table(new String[]{"Table", "Access", "Lookup Key", "Support-Relevant Fields", "Operational Purpose"}, new String[][]{{"schemaTable", "table"}, {"access"}, {"lookupKey"}, {"supportFields"}, {"operationalPurpose"}}),
      RunbookSection.KAFKA, new Table(new String[]{"Topic", "Direction", "Consumer Group", "Business Purpose", "Failure Handling"}, new String[][]{{"topic"}, {"direction"}, {"group", "consumerGroup"}, {"businessPurpose"}, {"failureHandling"}}),
      RunbookSection.ERRORS, errorTable(),
      RunbookSection.RETENTION, new Table(new String[]{"Data Store / Record", "Retention / Expiry", "Mechanism", "Archive Destination", "Support Impact"}, new String[][]{{"store", "record"}, {"retention", "expiry"}, {"mechanism"}, {"archiveDestination"}, {"supportImpact"}}),
      RunbookSection.MIGRATION, new Table(new String[]{"Migration", "Change Type", "Compatibility Risk", "Support Meaning"}, new String[][]{{"migration"}, {"changeType"}, {"compatibilityRisk"}, {"supportMeaning"}}));

  private static final Map<String, String> LABELS = Map.ofEntries(
      Map.entry("businessOwner", "Business Owner"), Map.entry("businessPurpose", "Business Purpose"), Map.entry("criticality", "Criticality"), Map.entry("escalationChannel", "Escalation Channel"), Map.entry("name", "Service Name"), Map.entry("serviceName", "Service Name"), Map.entry("supportOwner", "Support Owner"), Map.entry("environment", "Environment"), Map.entry("gitCommitSha", "Git Commit SHA"), Map.entry("processingModel", "Processing Model"), Map.entry("successMeaning", "Success Meaning"), Map.entry("scanStatus", "Scan Status"));

  private static Table errorTable() { return new Table(new String[]{"Error Code / Log Signature", "Result", "Possible Causes", "How to Confirm", "Support Action"}, new String[][]{{"errorCode", "signature", "code"}, {"result", "response"}, {"possibleCauses", "causes"}, {"howToConfirm", "confirmation"}, {"supportAction", "action"}}); }
  static Table table(RunbookSection section) { return TABLES.get(section); }
  static JsonNode content(JsonNode data, RunbookSection section) {
    return switch (section) {
      case DATASTORE -> datastore(data);
      case KAFKA -> kafka(data);
      case CAPACITY -> categorized(data, new String[][]{{"rateLimits", "Rate Limit"}, {"capacityConstraints", "Capacity Constraint"}});
      case ALERTS -> categorized(data, new String[][]{{"configuredAlerts", "Configured Alert"}, {"supportHealthChecks", "Support Health Check"}});
      default -> data.path(section.key);
    };
  }
  static JsonNode featureFlags(JsonNode data) { return data.path("featureFlags"); }
  private static ArrayNode datastore(JsonNode data) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    append(result, data.path("databaseTables"));
    for (JsonNode item : iterable(data.path("aerospike"))) {
      ObjectNode copy = item.deepCopy();
      if (copy.hasNonNull("namespace") && copy.hasNonNull("set")) copy.put("schemaTable", copy.path("namespace").asText() + "." + copy.path("set").asText());
      result.add(copy);
    }
    return result;
  }
  private static ArrayNode kafka(JsonNode data) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    for (JsonNode item : iterable(data.path("kafkaConsumers"))) { ObjectNode copy = item.deepCopy(); copy.put("direction", "CONSUMER / INBOUND"); result.add(copy); }
    for (JsonNode item : iterable(data.path("kafkaProducers"))) { ObjectNode copy = item.deepCopy(); copy.put("direction", "PRODUCER / OUTBOUND"); result.add(copy); }
    return result;
  }
  private static ArrayNode categorized(JsonNode data, String[][] sources) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    for (String[] source : sources) for (JsonNode item : iterable(data.path(source[0]))) { ObjectNode copy = item.deepCopy(); copy.put("category", source[1]); result.add(copy); }
    return result;
  }
  private static Iterable<JsonNode> iterable(JsonNode node) { return node.isArray() ? node : List.of(); }
  private static void append(ArrayNode target, JsonNode source) { for (JsonNode item : iterable(source)) target.add(item); }
  static String boundary() { return "L1/L2 do not replay events, change offsets, mutate production data, or change runtime/configuration."; }
  static String absence(JsonNode data) { return "PARTIAL".equalsIgnoreCase(data.path("generator").path("scanStatus").asText()) ? UNCONFIRMED : NOT_FOUND; }

  static String value(JsonNode data, JsonNode row, String[] alternatives) {
    for (String key : alternatives) {
      if ("schemaTable".equals(key) && row.hasNonNull("schema") && row.hasNonNull("table")) return readable(row.path("schema").asText() + "." + row.path("table").asText(), data);
      JsonNode value = row.get(key);
      if (value != null && !value.isNull() && !(value.isTextual() && value.asText().isBlank())) return readable(value, data);
    }
    return explicitNotApplicable(row) ? NOT_APPLICABLE : absence(data);
  }

  static String readable(JsonNode node, JsonNode data) {
    if (node == null || node.isNull() || node.isMissingNode() || (node.isTextual() && node.asText().isBlank())) return absence(data);
    if (explicitNotApplicable(node)) return NOT_APPLICABLE;
    if (node.isValueNode()) return node.asText();
    if (node.isArray()) { if (node.isEmpty()) return absence(data); List<String> values = new ArrayList<>(); node.forEach(value -> values.add(readable(value, data))); return String.join(", ", values); }
    List<String> values = new ArrayList<>();
    node.fields().forEachRemaining(entry -> { if (!internal(entry.getKey())) values.add(label(entry.getKey()) + ": " + readable(entry.getValue(), data)); });
    return values.isEmpty() ? absence(data) : String.join("; ", values);
  }
  static String readable(String value, JsonNode data) { return value == null || value.isBlank() ? absence(data) : value; }
  static boolean explicitNotApplicable(JsonNode node) {
    if (node == null || node.isNull()) return false;
    if (node.isTextual()) return "NOT_APPLICABLE".equalsIgnoreCase(node.asText()) || NOT_APPLICABLE.equalsIgnoreCase(node.asText());
    return node.isObject() && (node.path("notApplicable").asBoolean(false) || "NOT_APPLICABLE".equalsIgnoreCase(node.path("applicability").asText()));
  }
  static boolean internal(String name) { return "id".equals(name) || "stableId".equals(name); }
  static String label(String value) { String mapped = LABELS.get(value); if (mapped != null) return mapped; String spaced = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' '); return spaced.isBlank() ? value : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1); }
}
