package com.idfc.ai.runbook.api.dto;

import com.idfc.ai.runbook.orchestration.RunbookJob;
import com.idfc.ai.runbook.orchestration.RunbookJobState;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JobResponse(
    UUID jobId,
    String serviceId,
    String requestedCommitSha,
    String actualAnalyzedCommitSha,
    RunbookJobState status,
    boolean operationalChange,
    List<String> changedSections,
    Map<String, String> artifacts,
    String failureCode,
    String failureMessage
) {
  public static JobResponse from(RunbookJob j) {
    if (j == null) return null;
    return new JobResponse(
        j.id,
        j.serviceId != null ? j.serviceId : "",
        j.requestedCommit != null ? j.requestedCommit : "",
        j.analyzedCommit != null ? j.analyzedCommit : "",
        j.state != null ? j.state : RunbookJobState.RECEIVED,
        j.operationalChange,
        j.changedSections != null ? List.copyOf(j.changedSections) : List.of(),
        j.artifacts != null ? Map.copyOf(j.artifacts) : Map.of(),
        j.failureCode != null ? j.failureCode : "",
        j.failureMessage != null ? j.failureMessage : ""
    );
  }
}
