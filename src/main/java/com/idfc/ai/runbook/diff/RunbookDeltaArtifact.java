package com.idfc.ai.runbook.diff;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Contract-2.1 deterministic delta envelope around the existing semantic comparison. */
public final class RunbookDeltaArtifact {
  private RunbookDeltaArtifact() {}

  public static Map<String, Object> create(String previousIdentity, String currentIdentity, OperationalDiff diff) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("contractVersion", "2.1");
    value.put("previousBaselineIdentity", previousIdentity == null ? null : Map.of("identity", previousIdentity));
    value.put("currentIdentity", Map.of("identity", currentIdentity));
    value.put("hasOperationalChanges", diff.hasOperationalChanges());
    value.put("changedSections", diff.changedSections());
    value.put("changes", diff.changedSections().stream().map(section -> change(section, previousIdentity == null)).toList());
    return value;
  }

  private static Map<String, Object> change(String section, boolean firstRun) {
    Map<String, Object> change = new LinkedHashMap<>();
    change.put("type", firstRun ? "ADDED" : "MODIFIED");
    change.put("entityType", "RUNBOOK_SECTION");
    change.put("entityId", section);
    change.put("field", "sectionFingerprint");
    change.put("classification", "OPERATIONAL");
    change.put("sectionIds", List.of(section));
    return change;
  }
}
