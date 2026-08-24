package com.idfc.ai.runbook.validation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RunbookEvidenceValidator {
  private static final Pattern GENERATED_DIR_PATTERN = Pattern.compile("(?i)(?:^|[/\\\\])(?:build|target|out|\\.gradle|node_modules)[/\\\\]");

  public ValidationResult validate(JsonNode evidence) {
    List<String> errors = new ArrayList<>();
    for (JsonNode fact : evidence.path("facts")) {
      if (fact.path("factId").asText().isBlank()) {
        errors.add("empty factId");
      }
      for (JsonNode source : fact.path("sourceEvidence")) {
        String file = source.path("file").asText();
        if (file.startsWith("/") || file.matches("^[A-Za-z]:.*")) {
          errors.add("absolute evidence path: " + file);
        } else if (GENERATED_DIR_PATTERN.matcher(file).find()) {
          errors.add("evidence cites generated build directory: " + file);
        }
        if (source.path("lineStart").asInt() < 1 || source.path("lineEnd").asInt() < source.path("lineStart").asInt()) {
          errors.add("invalid evidence range in " + file);
        }
      }
    }
    return new ValidationResult(errors.isEmpty(), errors);
  }
}
