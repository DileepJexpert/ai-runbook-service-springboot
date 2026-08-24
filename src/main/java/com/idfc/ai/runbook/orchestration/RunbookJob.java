package com.idfc.ai.runbook.orchestration;

import com.idfc.ai.runbook.api.dto.CreateJobRequest;
import java.time.Instant;
import java.util.*;

public class RunbookJob {
  public final UUID id = UUID.randomUUID();
  public final String serviceId;
  public volatile String requestedCommit;
  public final String environment;
  public final String imageTag;
  public final CreateJobRequest.Repository repo;
  public final CreateJobRequest.ExtractionInput extractionInput;
  public volatile String analyzedCommit;
  public volatile String failureCode;
  public volatile String failureMessage;
  public volatile RunbookJobState state = RunbookJobState.RECEIVED;
  public volatile boolean operationalChange;
  public final List<String> changedSections = new ArrayList<>();
  public final Map<String, String> artifacts = new TreeMap<>();
  public final Instant receivedAt = Instant.now();
  public volatile Instant updatedAt = receivedAt;

  public RunbookJob(CreateJobRequest r) {
    this.serviceId = r.serviceId();
    this.requestedCommit = r.repository() != null ? r.repository().commitSha() : null;
    this.environment = r.deployment() != null && r.deployment().environment() != null ? r.deployment().environment() : "TEST";
    this.imageTag = r.deployment() != null ? r.deployment().imageTag() : null;
    this.repo = r.repository();
    this.extractionInput = r.extractionInput();
  }

  public synchronized void transition(RunbookJobState next) {
    if (state == RunbookJobState.FAILED || state == RunbookJobState.PUBLISHED) {
      throw new IllegalStateException("terminal job state");
    }
    state = next;
    updatedAt = Instant.now();
  }

  public synchronized void fail(String c, String m) {
    state = RunbookJobState.FAILED;
    failureCode = c;
    failureMessage = m;
    updatedAt = Instant.now();
  }
}
