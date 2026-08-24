package com.idfc.ai.runbook.quality;

import java.util.List;

/** Deterministic pipeline decision; it is never supplied by the extraction agent. */
public record QualityGateDecision(String outcome, boolean publishEligible, List<String> warnings, List<String> errors) {
  public static final String PASS = "PASS";
  public static final String RENDER_ALLOWED_PUBLISH_BLOCKED = "RENDER_ALLOWED_PUBLISH_BLOCKED";
  public static final String FAILED = "FAILED";
}
