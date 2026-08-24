package com.idfc.ai.runbook.normalization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class StableIdService {
  public String natural(String kind, String... parts) {
    StringBuilder value = new StringBuilder(kind.toUpperCase(Locale.ROOT));
    for (String part : parts) {
      if (part != null && !part.isBlank()) {
        value.append(':').append(normalizePart(part));
      }
    }
    return value.toString();
  }

  public String configuration(String componentId, String propertyKey) {
    if (componentId != null && !componentId.isBlank() && !"GLOBAL".equalsIgnoreCase(componentId)) {
      return natural("CONFIG", componentId, propertyKey);
    }
    return natural("CONFIG", propertyKey);
  }

  public String businessRule(String flowId, String sourceFile, String containingMethod, String condition) {
    String canonical = String.join("|", flowId, sourceFile, containingMethod, condition.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
    try { return "RULE:" + java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))).substring(0, 20); }
    catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
  }

  private String normalizePart(String value) { return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-").replaceAll("^-|-$", ""); }
}
