package com.idfc.ai.runbook.api;

import com.idfc.ai.runbook.api.dto.*;
import com.idfc.ai.runbook.orchestration.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runbooks/jobs")
public class RunbookJobController {
  private static final Logger log = LoggerFactory.getLogger(RunbookJobController.class);
  private final RunbookJobService service;

  public RunbookJobController(RunbookJobService s) {
    this.service = s;
  }

  @PostMapping
  public ResponseEntity<JobResponse> create(@Valid @RequestBody CreateJobRequest request) {
    RunbookJob job = service.create(request);
    log.info("jobId={} serviceId={} status={} - job created via API", job.id, job.serviceId, job.state);
    return ResponseEntity.accepted().body(JobResponse.from(job));
  }

  @GetMapping("/{id}")
  public ResponseEntity<JobResponse> get(@PathVariable("id") String id) {
    UUID uuid;
    try {
      uuid = UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new NoSuchElementException("RUNBOOK_JOB_NOT_FOUND: invalid job id " + id);
    }
    RunbookJob job = service.get(uuid);
    log.info("jobId={} serviceId={} status={} - job lookup via API", job.id, job.serviceId, job.state);
    return ResponseEntity.ok(JobResponse.from(job));
  }

  @PostMapping("/{id}/publish")
  public ResponseEntity<JobResponse> publish(@PathVariable("id") String id, @Valid @RequestBody PublishRequest request) {
    UUID uuid;
    try {
      uuid = UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new NoSuchElementException("RUNBOOK_JOB_NOT_FOUND: invalid job id " + id);
    }
    RunbookJob job = service.publish(uuid, request);
    log.info("jobId={} serviceId={} status={} - job published via API", job.id, job.serviceId, job.state);
    return ResponseEntity.ok(JobResponse.from(job));
  }
}
