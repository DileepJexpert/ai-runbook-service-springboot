package com.idfc.ai.runbook.rendering;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LeanMarkdownToHtmlConverter {

  public String convertToHtml(String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return "<!-- AUTO-GENERATED START -->\n<p>No content generated</p>\n<!-- AUTO-GENERATED END -->";
    }

    StringBuilder html = new StringBuilder("<!-- AUTO-GENERATED START -->\n");
    String[] lines = markdown.split("\\R");
    int i = 0;

    while (i < lines.length) {
      String line = lines[i].trim();

      if (line.isEmpty()) {
        i++;
        continue;
      }

      // Headers
      if (line.startsWith("# ")) {
        html.append("<h1>").append(formatInline(line.substring(2).trim())).append("</h1>\n");
        i++;
        continue;
      }
      if (line.startsWith("## ")) {
        html.append("<h2>").append(formatInline(line.substring(3).trim())).append("</h2>\n");
        i++;
        continue;
      }
      if (line.startsWith("### ")) {
        html.append("<h3>").append(formatInline(line.substring(4).trim())).append("</h3>\n");
        i++;
        continue;
      }
      if (line.startsWith("#### ")) {
        html.append("<h4>").append(formatInline(line.substring(5).trim())).append("</h4>\n");
        i++;
        continue;
      }

      // Markdown Table
      if (line.startsWith("|") && line.endsWith("|")) {
        List<String> tableLines = new ArrayList<>();
        while (i < lines.length && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
          tableLines.add(lines[i].trim());
          i++;
        }
        html.append(renderTable(tableLines)).append("\n");
        continue;
      }

      // Unordered list
      if (line.startsWith("- ") || line.startsWith("* ")) {
        html.append("<ul>\n");
        while (i < lines.length && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* "))) {
          String itemText = lines[i].trim().substring(2).trim();
          html.append("<li>").append(formatInline(itemText)).append("</li>\n");
          i++;
        }
        html.append("</ul>\n");
        continue;
      }

      // Code block
      if (line.startsWith("```")) {
        StringBuilder codeBlock = new StringBuilder("<pre><code>");
        i++;
        while (i < lines.length && !lines[i].trim().startsWith("```")) {
          codeBlock.append(escapeHtml(lines[i])).append("\n");
          i++;
        }
        if (i < lines.length && lines[i].trim().startsWith("```")) {
          i++;
        }
        codeBlock.append("</code></pre>\n");
        html.append(codeBlock);
        continue;
      }

      // Regular paragraph
      StringBuilder paragraph = new StringBuilder();
      while (i < lines.length && !lines[i].trim().isEmpty() &&
          !lines[i].trim().startsWith("#") &&
          !lines[i].trim().startsWith("|") &&
          !lines[i].trim().startsWith("- ") &&
          !lines[i].trim().startsWith("* ") &&
          !lines[i].trim().startsWith("```")) {
        if (paragraph.length() > 0) paragraph.append(" ");
        paragraph.append(lines[i].trim());
        i++;
      }
      html.append("<p>").append(formatInline(paragraph.toString())).append("</p>\n");
    }

    html.append("<!-- AUTO-GENERATED END -->");
    return html.toString();
  }

  private String renderTable(List<String> tableLines) {
    if (tableLines.isEmpty()) return "";
    StringBuilder sb = new StringBuilder("<table>\n");

    // Header
    String headerLine = tableLines.get(0);
    String[] headers = parseRow(headerLine);
    sb.append("<thead><tr>");
    for (String h : headers) {
      sb.append("<th>").append(formatInline(h)).append("</th>");
    }
    sb.append("</tr></thead>\n<tbody>\n");

    // Skip separator if present (line containing |---|---|)
    int startRow = 1;
    if (tableLines.size() > 1 && tableLines.get(1).replaceAll("[\\s|\\-:]", "").isEmpty()) {
      startRow = 2;
    }

    for (int r = startRow; r < tableLines.size(); r++) {
      String[] cells = parseRow(tableLines.get(r));
      sb.append("<tr>");
      for (int c = 0; c < headers.length; c++) {
        String cell = c < cells.length ? cells[c] : "";
        sb.append("<td>").append(formatInline(cell)).append("</td>");
      }
      sb.append("</tr>\n");
    }

    sb.append("</tbody></table>");
    return sb.toString();
  }

  private String[] parseRow(String line) {
    String stripped = line;
    if (stripped.startsWith("|")) stripped = stripped.substring(1);
    if (stripped.endsWith("|")) stripped = stripped.substring(0, stripped.length() - 1);
    String[] parts = stripped.split("\\|");
    for (int i = 0; i < parts.length; i++) {
      parts[i] = parts[i].trim();
    }
    return parts;
  }

  private String formatInline(String text) {
    if (text == null) return "";
    String escaped = escapeHtml(text);
    // Bold **text**
    escaped = escaped.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
    // Inline code `code`
    escaped = escaped.replaceAll("`(.+?)`", "<code>$1</code>");
    return escaped;
  }

  private String escapeHtml(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
