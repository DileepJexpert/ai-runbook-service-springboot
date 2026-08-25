package com.idfc.ai.runbook.validation;

import com.idfc.ai.runbook.agent.LeanRunbookPromptBuilder;
import com.idfc.ai.runbook.rendering.RunbookSection;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class LeanRunbookValidator {

  private final RunbookSafetyValidator safetyValidator;

  public LeanRunbookValidator(RunbookSafetyValidator safetyValidator) {
    this.safetyValidator = safetyValidator;
  }

  public ValidationResult validate(String markdown, String serviceId, String commitSha) {
    List<String> errors = new ArrayList<>();

    if (markdown == null || markdown.isBlank() || markdown.trim().length() < 100) {
      errors.add("RUNBOOK_MARKDOWN_EMPTY: Generated RUNBOOK.md is empty or insufficient in length");
      return new ValidationResult(false, errors);
    }

    // 1. Validate that all 23 headings exist in exact order
    int lastFoundIndex = -1;
    for (RunbookSection section : RunbookSection.values()) {
      String expectedHeading = section.heading;
      // Search for heading (case-insensitive) after the previous heading
      int foundIndex = findHeadingIndex(markdown, expectedHeading, lastFoundIndex);
      if (foundIndex < 0) {
        errors.add("RUNBOOK_MISSING_SECTION: Missing or out-of-order required section heading: " + expectedHeading);
      } else {
        lastFoundIndex = foundIndex;
      }
    }

    // 2. Secret and Safety validation on Markdown
    ValidationResult safetyResult = safetyValidator.validateMarkdown(markdown);
    if (!safetyResult.valid()) {
      errors.addAll(safetyResult.errors());
    }

    // 3. Service metadata consistency check
    if (serviceId != null && !serviceId.isBlank()) {
      String lower = markdown.toLowerCase(Locale.ROOT);
      if (!lower.contains(serviceId.toLowerCase(Locale.ROOT))) {
        errors.add("RUNBOOK_METADATA_MISMATCH: Generated runbook does not contain service identifier: " + serviceId);
      }
    }

    return new ValidationResult(errors.isEmpty(), errors);
  }

  private int findHeadingIndex(String markdown, String heading, int fromIndex) {
    String lowerMd = markdown.toLowerCase(Locale.ROOT);
    String lowerHeading = heading.toLowerCase(Locale.ROOT);

    int start = fromIndex >= 0 ? fromIndex : 0;
    int idx = lowerMd.indexOf(lowerHeading, start);
    if (idx >= 0) return idx;

    // Try without punctuation / special characters
    String cleanHeading = lowerHeading.replaceAll("[^a-z0-9]", " ").replaceAll("\\s+", " ").trim();
    int cleanIdx = lowerMd.indexOf(cleanHeading, start);
    return cleanIdx;
  }
}
