package com.idfc.ai.runbook.rendering;

import com.fasterxml.jackson.databind.JsonNode;
import com.idfc.ai.runbook.diff.OperationalDiff;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Fixed, deterministic views of canonical facts; none are AI-authored prose. */
@Component
public class SupplementalArtifactRenderer {
  public Map<String, String> render(JsonNode data, OperationalDiff delta) {
    Map<String, String> artifacts = new LinkedHashMap<>();
    artifacts.put("CONFIGURATION-CATALOG.md", catalog(data, "Configuration Catalog", "configuration",
        new String[]{"Component", "Purpose", "Property Key", "Config Portal Key", "Repository Value", "Repository Default", "Runtime Value Status", "Sensitive", "Support Lookup"},
        new String[][]{{"componentId", "componentName", "modulePath", "deploymentUnit"}, {"logicalPurpose"}, {"propertyKey"}, {"configKey"}, {"repositoryValue"}, {"repositoryDefault"}, {"runtimeValueStatus"}, {"sensitive"}, {"supportLookupInstruction"}}));
    artifacts.put("API-CATALOG.md", catalog(data, "API Catalog", "apis", new String[]{"Endpoint/Event", "Method/Direction", "Processing Model", "Success Meaning"}, new String[][]{{"path", "endpoint", "event"}, {"method", "direction"}, {"processingModel"}, {"successMeaning"}}));
    artifacts.put("BUSINESS-RULES.md", catalog(data, "Business Rules", "businessRules", new String[]{"Business Condition", "Data Used", "Result / Response", "Regulatory Basis"}, new String[][]{{"condition"}, {"dataUsed", "source"}, {"result", "response"}, {"regulatoryBasis"}}));
    artifacts.put("OBSERVABILITY-CATALOG.md", observability(data));
    artifacts.put("ARCHITECTURE.md", architecture(data));
    artifacts.put("RELEASE-IMPACT.md", releaseImpact(delta));
    return artifacts;
  }

  private String catalog(JsonNode data, String title, String key, String[] headers, String[][] fields) {
    StringBuilder out = new StringBuilder("# ").append(title).append("\n\n");
    JsonNode rows = data.path(key);
    if (!rows.isArray() || rows.isEmpty()) return out.append(SupportContentFormatter.absence(data)).append("\n").toString();
    out.append("| ").append(String.join(" | ", headers)).append(" |\n| ");
    for (int i = 0; i < headers.length; i++) { if (i > 0) out.append(" | "); out.append("---"); }
    out.append(" |\n");
    for (JsonNode row : rows) { out.append("| "); for (int i = 0; i < fields.length; i++) { if (i > 0) out.append(" | "); out.append(cell(SupportContentFormatter.value(data, row, fields[i]))); } out.append(" |\n"); }
    return out.toString();
  }
  private String observability(JsonNode data) {
    StringBuilder out = new StringBuilder("# Observability Catalog\n\n");
    appendList(out, data, "Metrics", data.path("metrics"));
    appendList(out, data, "Health Checks", data.path("supportHealthChecks"));
    appendList(out, data, "Configured Alerts", data.path("configuredAlerts"));
    appendList(out, data, "Trace Identifiers", data.path("traceIdentifiers"));
    return out.toString();
  }
  private String architecture(JsonNode data) {
    StringBuilder out = new StringBuilder("# Observed Repository Architecture\n\n");
    out.append("This is an observed repository inventory, not an official target architecture.\n\n");
    appendList(out, data, "Service", data.path("service"));
    appendList(out, data, "Entry Points", data.path("apis"));
    appendList(out, data, "Datastores", SupportContentFormatter.content(data, RunbookSection.DATASTORE));
    appendList(out, data, "Kafka", SupportContentFormatter.content(data, RunbookSection.KAFKA));
    appendList(out, data, "Downstream Dependencies", data.path("downstreamDependencies"));
    return out.toString();
  }
  private String releaseImpact(OperationalDiff delta) {
    StringBuilder out = new StringBuilder("# Release Impact\n\n");
    if (!delta.hasOperationalChanges()) return out.append("No operational changes detected.\n").toString();
    out.append("Operationally changed sections: ").append(String.join(", ", delta.changedSections())).append(".\n");
    return out.toString();
  }
  private void appendList(StringBuilder out, JsonNode data, String title, JsonNode values) {
    out.append("## ").append(title).append("\n\n");
    if (values == null || values.isMissingNode() || values.isNull() || values.isEmpty()) { out.append(SupportContentFormatter.absence(data)).append("\n\n"); return; }
    if (values.isArray()) for (JsonNode value : values) out.append("- ").append(SupportContentFormatter.readable(value, data)).append("\n");
    else out.append(SupportContentFormatter.readable(values, data)).append("\n");
    out.append("\n");
  }
  private String cell(String value) { return value.replace("|", "\\|").replace("\n", " "); }
}
