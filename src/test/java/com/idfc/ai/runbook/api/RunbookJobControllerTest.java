package com.idfc.ai.runbook.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfc.ai.runbook.agent.FakeIdfcCoderExecutor;
import com.idfc.ai.runbook.api.dto.CreateJobRequest;
import com.idfc.ai.runbook.orchestration.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeIdfcCoderExecutor.class)
class RunbookJobControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private RunbookJobStore jobStore;
  @Autowired private ObjectMapper mapper;

  @Test
  void post_creates_job_and_get_immediately_retrieves_same_job() throws Exception {
    CreateJobRequest request = new CreateJobRequest(
        "test-service",
        new CreateJobRequest.Repository("BITBUCKET", null, "https://bitbucket.bank.local/scm/test/test.git", "main", "1234567"),
        new CreateJobRequest.Deployment("TEST")
    );

    String responseBody = mvc.perform(post("/api/v1/runbooks/jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").isNotEmpty())
        .andExpect(jsonPath("$.serviceId").value("test-service"))
        .andExpect(jsonPath("$.status").isNotEmpty())
        .andReturn().getResponse().getContentAsString();

    String jobId = mapper.readTree(responseBody).path("jobId").asText();

    mvc.perform(get("/api/v1/runbooks/jobs/" + jobId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId").value(jobId))
        .andExpect(jsonPath("$.serviceId").value("test-service"))
        .andExpect(jsonPath("$.status").isNotEmpty());
  }

  @Test
  void get_job_in_preparing_workspace_state_returns_200() throws Exception {
    CreateJobRequest request = new CreateJobRequest(
        "workspace-prep-service",
        new CreateJobRequest.Repository("BITBUCKET", null, "https://bitbucket.bank.local/scm/test/test.git", "main", "1234567"),
        new CreateJobRequest.Deployment("TEST")
    );
    RunbookJob job = new RunbookJob(request);
    job.transition(RunbookJobState.PREPARING_WORKSPACE);
    jobStore.save(job);

    mvc.perform(get("/api/v1/runbooks/jobs/" + job.id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId").value(job.id.toString()))
        .andExpect(jsonPath("$.serviceId").value("workspace-prep-service"))
        .andExpect(jsonPath("$.status").value("PREPARING_WORKSPACE"));
  }

  @Test
  void get_job_in_extracting_state_returns_200() throws Exception {
    CreateJobRequest request = new CreateJobRequest(
        "extracting-service",
        new CreateJobRequest.Repository("BITBUCKET", null, "https://bitbucket.bank.local/scm/test/test.git", "main", "1234567"),
        new CreateJobRequest.Deployment("TEST")
    );
    RunbookJob job = new RunbookJob(request);
    job.transition(RunbookJobState.PREPARING_WORKSPACE);
    job.transition(RunbookJobState.EXTRACTING);
    jobStore.save(job);

    mvc.perform(get("/api/v1/runbooks/jobs/" + job.id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId").value(job.id.toString()))
        .andExpect(jsonPath("$.serviceId").value("extracting-service"))
        .andExpect(jsonPath("$.status").value("EXTRACTING"));
  }

  @Test
  void get_job_in_failed_state_returns_200_with_failure_details() throws Exception {
    CreateJobRequest request = new CreateJobRequest(
        "failing-service",
        new CreateJobRequest.Repository("BITBUCKET", null, "https://bitbucket.bank.local/scm/test/test.git", "nonexistent-branch", null),
        new CreateJobRequest.Deployment("TEST")
    );
    RunbookJob job = new RunbookJob(request);
    job.fail("RUNBOOK_BRANCH_NOT_FOUND", "RUNBOOK_BRANCH_NOT_FOUND: branch 'nonexistent-branch' not found on remote");
    jobStore.save(job);

    mvc.perform(get("/api/v1/runbooks/jobs/" + job.id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId").value(job.id.toString()))
        .andExpect(jsonPath("$.serviceId").value("failing-service"))
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.failureCode").value("RUNBOOK_BRANCH_NOT_FOUND"))
        .andExpect(jsonPath("$.failureMessage").value("RUNBOOK_BRANCH_NOT_FOUND: branch 'nonexistent-branch' not found on remote"));
  }

  @Test
  void get_unknown_job_returns_404_not_found() throws Exception {
    UUID unknownId = UUID.randomUUID();
    mvc.perform(get("/api/v1/runbooks/jobs/" + unknownId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RUNBOOK_JOB_NOT_FOUND"))
        .andExpect(jsonPath("$.message").isNotEmpty());

    mvc.perform(get("/api/v1/runbooks/jobs/not-a-valid-uuid"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RUNBOOK_JOB_NOT_FOUND"));
  }

  @Test
  void background_failure_preserves_job_in_store_with_failed_status() throws Exception {
    CreateJobRequest request = new CreateJobRequest(
        "invalid-repo-service",
        new CreateJobRequest.Repository("LOCAL_PATH", "/non/existent/path/for/test/12345", null),
        new CreateJobRequest.Deployment("TEST")
    );

    String responseBody = mvc.perform(post("/api/v1/runbooks/jobs")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(request)))
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getContentAsString();

    String jobId = mapper.readTree(responseBody).path("jobId").asText();
    UUID uuid = UUID.fromString(jobId);

    // Wait for background execution to complete and transition to FAILED
    RunbookJob job = null;
    for (int i = 0; i < 100; i++) {
      job = jobStore.get(uuid).orElse(null);
      if (job != null && job.state == RunbookJobState.FAILED) break;
      Thread.sleep(25);
    }

    assertThat(job).isNotNull();
    assertThat(job.state).isEqualTo(RunbookJobState.FAILED);
    assertThat(job.failureCode).isNotEmpty();

    // Verify GET endpoint returns HTTP 200 for the FAILED job
    mvc.perform(get("/api/v1/runbooks/jobs/" + jobId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId").value(jobId))
        .andExpect(jsonPath("$.status").value("FAILED"))
        .andExpect(jsonPath("$.failureCode").isNotEmpty())
        .andExpect(jsonPath("$.failureMessage").isNotEmpty());
  }
}
