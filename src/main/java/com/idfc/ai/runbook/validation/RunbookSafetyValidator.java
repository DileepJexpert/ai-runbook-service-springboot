package com.idfc.ai.runbook.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class RunbookSafetyValidator {

  public record SafetyFinding(
      String code,
      String jsonPath,
      String ruleId,
      String diagnostic
  ) {
    public String formattedError() {
      return String.format("[%s] at %s (rule: %s): %s", code, jsonPath, ruleId, diagnostic);
    }
  }

  // --- Secret Rules (Applied globally to ALL textual fields in JSON) ---
  private static final List<Rule> SECRET_RULES = List.of(
      new Rule("NO_PRIVATE_KEY",
          Pattern.compile("(?is)-----BEGIN\\s+(?:[A-Z\\s]+)?PRIVATE\\s+KEY-----"),
          "Private key header detected in JSON field"),
      new Rule("NO_BEARER_TOKEN",
          Pattern.compile("(?is)\\bbearer\\s+[a-z0-9._\\-]{12,}\\b"),
          "Bearer token value detected in JSON field"),
      new Rule("NO_PASSWORD_ASSIGNMENT",
          Pattern.compile("(?is)\\b(?:password|passwd|pwd)\\s*[:=]\\s*[^\\s,;\"]{3,}"),
          "Plaintext password assignment detected in JSON field"),
      new Rule("NO_SECRET_ASSIGNMENT",
          Pattern.compile("(?is)\\b(?:api[_-]?key|client[_-]?secret|auth[_-]?token|secret[_-]?key)\\s*[:=]\\s*[^\\s,;\"]{8,}"),
          "Secret credential assignment detected in JSON field")
  );

  // --- Negation Pattern for Explicit Prohibitions ---
  private static final Pattern PROHIBITION_NEGATION = Pattern.compile(
      "(?is)\\b(?:do\\s+not|don't|does\\s+not|doesn't|never|must\\s+not|mustn't|should\\s+not|shouldn't|shall\\s+not|cannot|can't|prohibited|forbidden|avoid|refrain(?:\\s+from)?|no\\s+manual|without\\s+manual|not\\s+to|strictly\\s+prohibited)\\b"
  );

  // --- Operational Safety Rules (Applied primarily to support-facing fields) ---
  private static final List<Rule> OPERATIONAL_SAFETY_RULES = List.of(
      new Rule("NO_MANUAL_DATABASE_MUTATION",
          Pattern.compile("(?is)\\b(?:update|delete|modify|mutate|insert|drop|truncate|alter)\\b.{0,40}\\b(?:database|db|record|table|row)s?\\b"),
          "Affirmative instruction to manually mutate database rows or records"),
      new Rule("NO_EVENT_REPLAY",
          Pattern.compile("(?is)\\b(?:replay|republish|re-publish|re-send|resend)\\b.{0,40}\\b(?:kafka|event|message|topic)s?\\b"),
          "Affirmative instruction to replay or republish Kafka events"),
      new Rule("NO_OFFSET_MUTATION",
          Pattern.compile("(?is)\\b(?:change|alter|reset|modify|advance|rewind|seek|set)\\b.{0,40}\\b(?:kafka\\s+)?(?:consumer\\s+)?offsets?\\b"),
          "Affirmative instruction to alter Kafka consumer offsets"),
      new Rule("NO_PROD_CONFIG_MUTATION",
          Pattern.compile("(?is)\\b(?:modify|change|edit|update|override)\\b.{0,40}\\b(?:production|prod)\\b.{0,20}\\b(?:config|configuration)\\b|\\b(?:modify|change|edit|update)\\s+production\\s+config\\b"),
          "Affirmative instruction to modify production configuration"),
      new Rule("NO_MANUAL_REPROCESSING",
          Pattern.compile("(?is)\\b(?:reprocess|re-process|retry\\s+transaction)\\b.{0,40}\\b(?:manually|payment|transaction|request)\\b|\\bmanually\\b.{0,40}\\b(?:reprocess|re-process)\\b|\\b(?:reprocess|re-process)\\s+(?:the\s+)?(?:payment|transaction|record)\s+manually\\b"),
          "Affirmative instruction to manually reprocess transactions or payments"),
      new Rule("NO_RESTART_SCALE_INSTRUCTION",
          Pattern.compile("(?is)\\b(?:restart|scale|kill|terminate|bounce)\\b.{0,40}\\b(?:service|pod|container|app|deployment|instance|cluster)s?\\b"),
          "Affirmative instruction to restart or scale services as a recovery action"),
      new Rule("NO_MANUAL_STATE_RECONCILIATION",
          Pattern.compile("(?is)\\b(?:initiate|trigger|run|execute|perform|force|start)\\b.{0,50}\\b(?:reconciliation|manual\\s+reconciliation|state-changing\\s+reconciliation)\\b|\\b(?:reconciliation)\\s+(?:manually|for this payment)\\b|\\bmanual\\s+reconciliation\\b"),
          "Affirmative instruction to initiate, trigger, or perform manual or state-changing reconciliation"),
      new Rule("NO_FORCE_SCHEDULER",
          Pattern.compile("(?is)\\b(?:force|manually\\s+trigger|manually\\s+run)\\b.{0,40}\\b(?:scheduler|schedulers|cron|scheduled\\s+job|worker)s?\\b"),
          "Affirmative instruction to force or manually trigger schedulers")
  );

  // Set of field names considered support-facing
  private static final Set<String> SUPPORT_FIELD_NAMES = Set.of(
      "supportcheck", "guidance", "supportaction", "resolutionguidance", "troubleshooting",
      "supportinstructions", "howtoconfirm", "suggestedaction", "supportmeaning",
      "supportimpact", "resolutionaction", "supportlookup", "supportlookupinstruction",
      "supportguidance", "triageteamguidance", "triageguidance"
  );

  // Set of root/section names where all text fields are support-facing
  private static final Set<String> SUPPORT_SECTIONS = Set.of(
      "unknownincidentcontext", "escalationevidence", "supporthealthchecks", "supportclassificationrules"
  );

  public ValidationResult validate(JsonNode data) {
    List<SafetyFinding> findings = inspect(data);
    if (findings.isEmpty()) {
      return ValidationResult.ok();
    }
    List<String> errors = findings.stream().map(SafetyFinding::formattedError).toList();
    return new ValidationResult(false, errors);
  }

  public List<SafetyFinding> inspect(JsonNode data) {
    List<SafetyFinding> findings = new ArrayList<>();
    if (data != null) {
      walk(data, "$", findings);
    }
    return findings;
  }

  private void walk(JsonNode node, String path, List<SafetyFinding> findings) {
    if (node == null || node.isNull()) return;

    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        String fieldName = entry.getKey();
        String fieldPath = path.equals("$") ? "$." + fieldName : path + "." + fieldName;
        walk(entry.getValue(), fieldPath, findings);
      }
    } else if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        walk(node.get(i), path + "[" + i + "]", findings);
      }
    } else if (node.isTextual()) {
      String text = node.asText();
      checkText(text, path, findings);
    }
  }

  private void checkText(String text, String path, List<SafetyFinding> findings) {
    if (text == null || text.isBlank()) return;

    // 1. Secret Detection across ALL fields
    for (Rule rule : SECRET_RULES) {
      if (rule.pattern().matcher(text).find()) {
        findings.add(new SafetyFinding(
            "SECRET_VALUE_DETECTED",
            path,
            rule.ruleId(),
            rule.diagnostic()
        ));
      }
    }

    // 2. Operational Safety Checks restricted to Support-Facing Fields
    if (isSupportFacingField(path)) {
      String[] clauses = text.split("[\\.\\;\\!\\?\\n]+|,\\s*(?:but|however|whereas|although)\\s*");
      for (String clause : clauses) {
        String trimmed = clause.trim();
        if (trimmed.isEmpty()) continue;

        boolean isNegated = PROHIBITION_NEGATION.matcher(trimmed).find();
        if (!isNegated) {
          for (Rule rule : OPERATIONAL_SAFETY_RULES) {
            if (rule.pattern().matcher(trimmed).find()) {
              findings.add(new SafetyFinding(
                  "SAFETY_POLICY_VIOLATION",
                  path,
                  rule.ruleId(),
                  rule.diagnostic()
              ));
            }
          }
        }
      }
    }
  }

  private boolean isSupportFacingField(String path) {
    String lower = path.toLowerCase();
    for (String section : SUPPORT_SECTIONS) {
      if (lower.contains("." + section) || lower.startsWith("$." + section)) {
        return true;
      }
    }
    String lastPart = path;
    int lastDot = path.lastIndexOf('.');
    if (lastDot >= 0) {
      lastPart = path.substring(lastDot + 1);
    }
    int bracket = lastPart.indexOf('[');
    if (bracket >= 0) {
      lastPart = lastPart.substring(0, bracket);
    }
    return SUPPORT_FIELD_NAMES.contains(lastPart.toLowerCase());
  }

  private record Rule(String ruleId, Pattern pattern, String diagnostic) {}
}
