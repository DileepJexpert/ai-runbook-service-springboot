package com.idfc.ai.runbook.rendering;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ConfluenceRunbookRenderer implements RunbookRenderer {
  public String render(JsonNode data) {
    StringBuilder out = new StringBuilder("<!-- AUTO-GENERATED START -->\n<h1>Production Support Runbook — ").append(e(data.path("service").path("name").asText("Unknown Service"))).append("</h1>\n");
    for (RunbookSection section : RunbookSection.values()) out.append("<h2>").append(e(section.heading)).append("</h2>\n").append(renderSection(data, section)).append("\n");
    return out.append("<!-- AUTO-GENERATED END -->").toString();
  }
  private String renderSection(JsonNode data, RunbookSection section) {
    if (section == RunbookSection.BOUNDARY) return "<p>" + e(SupportContentFormatter.boundary()) + "</p>";
    JsonNode content = SupportContentFormatter.content(data, section);
    if (section == RunbookSection.MIGRATION) return deploymentAndFlags(data, content);
    if (content.isMissingNode() || content.isNull() || content.isEmpty()) return paragraph(SupportContentFormatter.absence(data));
    SupportContentFormatter.Table table = SupportContentFormatter.table(section);
    if (table != null) return table(data, content, table);
    if (section == RunbookSection.FLOW && content.isArray()) return flows(data, content);
    if (content.isObject()) return labels(data, content);
    if (content.isArray()) { StringBuilder out = new StringBuilder("<ul>"); for (JsonNode item : content) out.append("<li>").append(item.isObject() ? listItem(data, item) : e(SupportContentFormatter.readable(item, data))).append("</li>"); return out.append("</ul>").toString(); }
    return paragraph(SupportContentFormatter.readable(content, data));
  }
  private String table(JsonNode data, JsonNode rows, SupportContentFormatter.Table table) {
    if (!rows.isArray() || rows.isEmpty()) return paragraph(SupportContentFormatter.absence(data));
    StringBuilder out = new StringBuilder("<table><thead><tr>");
    for (String column : table.columns()) out.append("<th>").append(e(column)).append("</th>");
    out.append("</tr></thead><tbody>");
    for (JsonNode row : rows) { out.append("<tr>"); for (String[] fields : table.fields()) out.append("<td>").append(formatCell(SupportContentFormatter.value(data, row, fields))).append("</td>"); out.append("</tr>"); }
    return out.append("</tbody></table>").toString();
  }
  private String flows(JsonNode data, JsonNode flows) {
    List<String> rendered = new ArrayList<>();
    for (JsonNode flow : flows) {
      List<String> paragraphs = new ArrayList<>();
      if (flow.has("trigger")) paragraphs.add("<p><strong>Trigger:</strong> " + e(SupportContentFormatter.readable(flow.get("trigger"), data)) + "</p>");
      if (flow.has("outcome")) paragraphs.add("<p><strong>Outcome:</strong> " + e(SupportContentFormatter.readable(flow.get("outcome"), data)) + "</p>");
      flow.fields().forEachRemaining(entry -> { if (!"trigger".equals(entry.getKey()) && !"outcome".equals(entry.getKey()) && !SupportContentFormatter.internal(entry.getKey())) paragraphs.add("<p><strong>" + e(SupportContentFormatter.label(entry.getKey())) + ":</strong> " + e(SupportContentFormatter.readable(entry.getValue(), data)) + "</p>"); });
      rendered.add(paragraphs.isEmpty() ? paragraph(SupportContentFormatter.absence(data)) : String.join("", paragraphs));
    }
    return String.join("\n", rendered);
  }
  private String labels(JsonNode data, JsonNode object) { StringBuilder out = new StringBuilder("<ul>"); object.fields().forEachRemaining(entry -> { if (!SupportContentFormatter.internal(entry.getKey())) out.append("<li><strong>").append(e(SupportContentFormatter.label(entry.getKey()))).append(":</strong> ").append(e(SupportContentFormatter.readable(entry.getValue(), data))).append("</li>"); }); return out.append("</ul>").toString(); }
  private String listItem(JsonNode data, JsonNode object) { List<String> values = new ArrayList<>(); object.fields().forEachRemaining(entry -> { if (!SupportContentFormatter.internal(entry.getKey())) values.add("<strong>" + e(SupportContentFormatter.label(entry.getKey())) + ":</strong> " + e(SupportContentFormatter.readable(entry.getValue(), data))); }); return values.isEmpty() ? e(SupportContentFormatter.absence(data)) : String.join("<br/>", values); }
  private String deploymentAndFlags(JsonNode data, JsonNode migrations) {
    List<String> blocks = new ArrayList<>();
    if (migrations.isArray() && !migrations.isEmpty()) blocks.add(table(data, migrations, SupportContentFormatter.table(RunbookSection.MIGRATION)));
    JsonNode flags = SupportContentFormatter.featureFlags(data);
    if (flags.isArray() && !flags.isEmpty()) {
      StringBuilder out = new StringBuilder("<p><strong>Feature Flags</strong></p><ul>");
      for (JsonNode flag : flags) out.append("<li>").append(listItem(data, flag)).append("</li>");
      blocks.add(out.append("</ul>").toString());
    }
    return blocks.isEmpty() ? paragraph(SupportContentFormatter.absence(data)) : String.join("\n", blocks);
  }
  private String paragraph(String value) { return "<p>" + e(value) + "</p>"; }
  private String formatCell(String value) { return technicalIdentifier(value) ? "<code>" + e(value) + "</code>" : e(value); }
  private boolean technicalIdentifier(String value) { return value.matches("[A-Z][A-Z0-9_]*_[A-Z0-9_]*") || value.matches("[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*"); }
  private String e(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
}
