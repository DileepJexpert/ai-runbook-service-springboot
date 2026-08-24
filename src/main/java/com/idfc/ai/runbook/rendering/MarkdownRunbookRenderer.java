package com.idfc.ai.runbook.rendering;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MarkdownRunbookRenderer implements RunbookRenderer {
  public String render(JsonNode data) {
    StringBuilder out = new StringBuilder("# Production Support Runbook — ").append(data.path("service").path("name").asText("Unknown Service")).append("\n\n");
    for (RunbookSection section : RunbookSection.values()) out.append("## ").append(section.heading).append("\n\n").append(renderSection(data, section)).append("\n\n");
    return out.toString();
  }
  private String renderSection(JsonNode data, RunbookSection section) {
    if (section == RunbookSection.BOUNDARY) return SupportContentFormatter.boundary();
    JsonNode content = SupportContentFormatter.content(data, section);
    if (section == RunbookSection.MIGRATION) return deploymentAndFlags(data, content);
    if (content.isMissingNode() || content.isNull() || content.isEmpty()) return SupportContentFormatter.absence(data);
    SupportContentFormatter.Table table = SupportContentFormatter.table(section);
    if (table != null) return table(data, content, table);
    if (section == RunbookSection.FLOW && content.isArray()) return flows(data, content);
    if (content.isObject()) return labels(data, content);
    if (content.isArray()) { StringBuilder out = new StringBuilder(); for (JsonNode item : content) { String rendered = item.isObject() ? labels(data, item).replace("\n", "; ") : SupportContentFormatter.readable(item, data); if (!rendered.isBlank()) out.append("- ").append(rendered).append("\n"); } return out.toString().stripTrailing(); }
    return SupportContentFormatter.readable(content, data);
  }
  private String table(JsonNode data, JsonNode rows, SupportContentFormatter.Table table) {
    if (!rows.isArray() || rows.isEmpty()) return SupportContentFormatter.absence(data);
    String separator = Arrays.stream(table.columns()).map(column -> "-".repeat(column.length())).collect(java.util.stream.Collectors.joining(" | "));
    StringBuilder out = new StringBuilder("| ").append(String.join(" | ", table.columns())).append(" |\n| ").append(separator).append(" |");
    for (JsonNode row : rows) { out.append("\n| "); List<String> values = new ArrayList<>(); for (String[] fields : table.fields()) values.add(formatCell(SupportContentFormatter.value(data, row, fields))); out.append(String.join(" | ", values)).append(" |"); }
    return out.toString();
  }
  private String flows(JsonNode data, JsonNode flows) {
    List<String> rendered = new ArrayList<>();
    for (JsonNode flow : flows) {
      List<String> lines = new ArrayList<>();
      if (flow.has("trigger")) lines.add("**Trigger:** " + SupportContentFormatter.readable(flow.get("trigger"), data));
      if (flow.has("outcome")) lines.add("**Outcome:** " + SupportContentFormatter.readable(flow.get("outcome"), data));
      flow.fields().forEachRemaining(entry -> { if (!"trigger".equals(entry.getKey()) && !"outcome".equals(entry.getKey()) && !SupportContentFormatter.internal(entry.getKey())) lines.add("**" + SupportContentFormatter.label(entry.getKey()) + ":** " + SupportContentFormatter.readable(entry.getValue(), data)); });
      rendered.add(lines.isEmpty() ? SupportContentFormatter.absence(data) : String.join("\n\n", lines));
    }
    return String.join("\n\n", rendered);
  }
  private String labels(JsonNode data, JsonNode object) {
    List<String> values = new ArrayList<>();
    object.fields().forEachRemaining(entry -> { if (!SupportContentFormatter.internal(entry.getKey())) values.add("**" + SupportContentFormatter.label(entry.getKey()) + ":** " + SupportContentFormatter.readable(entry.getValue(), data)); });
    return values.isEmpty() ? SupportContentFormatter.absence(data) : String.join("\n", values);
  }
  private String deploymentAndFlags(JsonNode data, JsonNode migrations) {
    List<String> blocks = new ArrayList<>();
    if (migrations.isArray() && !migrations.isEmpty()) blocks.add(table(data, migrations, SupportContentFormatter.table(RunbookSection.MIGRATION)));
    JsonNode flags = SupportContentFormatter.featureFlags(data);
    if (flags.isArray() && !flags.isEmpty()) {
      StringBuilder out = new StringBuilder("**Feature Flags**\n");
      for (JsonNode flag : flags) out.append("- ").append(labels(data, flag).replace("\n", "; ")).append("\n");
      blocks.add(out.toString().stripTrailing());
    }
    return blocks.isEmpty() ? SupportContentFormatter.absence(data) : String.join("\n\n", blocks);
  }
  private String formatCell(String value) {
    String escaped = value.replace("|", "\\|").replace("\n", " ");
    return technicalIdentifier(value) ? "`" + escaped + "`" : escaped;
  }
  private boolean technicalIdentifier(String value) {
    return value.matches("[A-Z][A-Z0-9_]*_[A-Z0-9_]*") || value.matches("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*");
  }
}
