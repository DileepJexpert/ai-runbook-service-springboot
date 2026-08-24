package com.idfc.ai.runbook;

import com.idfc.ai.runbook.agent.FakeIdfcCoderExecutor;
import com.idfc.ai.runbook.api.dto.CreateJobRequest;
import com.idfc.ai.runbook.api.dto.PublishRequest;
import com.idfc.ai.runbook.orchestration.*;
import com.idfc.ai.runbook.repository.RemoteGitResolver;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(FakeIdfcCoderExecutor.class)
class RunbookJobIntegrationTest {
  @Autowired RunbookJobService service;
  @MockBean RemoteGitResolver remoteGitResolver;

  @Test void local_path_mode_generates_and_publishes() throws Exception {
    Files.deleteIfExists(Path.of("target", "test-artifacts", "baselines", "payments-service-TEST.json"));
    String repo = temporaryGitRepository();
    String sha = git(repo);
    var request = new CreateJobRequest("payments-service", new CreateJobRequest.Repository("LOCAL_PATH", repo, sha), new CreateJobRequest.Deployment("TEST", "1.0", "payments:1.0", "1", "test", "payments"));
    RunbookJob job = service.create(request);
    for (int i=0; i<400 && job.state != RunbookJobState.READY_TO_PUBLISH && job.state != RunbookJobState.FAILED; i++) Thread.sleep(25);
    assertThat(job.state).withFailMessage("state=%s, failure=%s: %s", job.state, job.failureCode, job.failureMessage).isEqualTo(RunbookJobState.READY_TO_PUBLISH);
    assertThat(job.artifacts).containsKey("root");
    Path root = Path.of(job.artifacts.get("root"));
    assertThat(root).isDirectory();
    assertThat(root.resolve("extraction/runbook-data.json")).isRegularFile();
    assertThat(root.resolve("extraction/runbook-evidence.json")).isRegularFile();
    assertThat(root.resolve("validation/validation-report.json")).isRegularFile();
    assertThat(root.resolve("normalized/normalized-runbook-data.json")).isRegularFile();
    assertThat(root.resolve("diff/runbook-delta.json")).isRegularFile();
    assertThat(root.resolve("report/generation-report.json")).isRegularFile();
    String generationReport = Files.readString(root.resolve("report/generation-report.json"));
    assertThat(generationReport).contains("\"artifactSha256\"", "diff/runbook-delta.json", "\"apis\"");
    for (String artifact : new String[]{"RUNBOOK.md", "confluence-body.html", "CONFIGURATION-CATALOG.md", "API-CATALOG.md", "BUSINESS-RULES.md", "OBSERVABILITY-CATALOG.md", "ARCHITECTURE.md", "RELEASE-IMPACT.md"}) assertThat(root.resolve("render").resolve(artifact)).isRegularFile();
    service.publish(job.id, new PublishRequest("TEST", sha, "payments:1.0"));
    assertThat(job.state).isEqualTo(RunbookJobState.PUBLISHED);
  }

  @Test void bitbucket_mode_generates_runbook_using_remote_resolution() throws Exception {
    when(remoteGitResolver.resolveRemoteHead(eq("https://bitbucket.bank.local/scm/pay/payments.git"), eq("develop"))).thenReturn("develop-head-sha-9999");

    var request = new CreateJobRequest(
        "bitbucket-payments-service",
        new CreateJobRequest.Repository("BITBUCKET", null, "https://bitbucket.bank.local/scm/pay/payments.git", "develop", null),
        new CreateJobRequest.Deployment("TEST")
    );

    RunbookJob job = service.create(request);
    for (int i=0; i<400 && job.state != RunbookJobState.READY_TO_PUBLISH && job.state != RunbookJobState.FAILED; i++) Thread.sleep(25);
    assertThat(job.state).withFailMessage("state=%s, failure=%s: %s", job.state, job.failureCode, job.failureMessage).isEqualTo(RunbookJobState.READY_TO_PUBLISH);
    assertThat(job.requestedCommit).isEqualTo("develop-head-sha-9999");
    assertThat(job.artifacts).containsKey("root");
    Path root = Path.of(job.artifacts.get("root"));
    assertThat(root.resolve("render/RUNBOOK.md")).isRegularFile();
    assertThat(root.resolve("render/confluence-body.html")).isRegularFile();
  }

  private String temporaryGitRepository() throws Exception { Path repo=Files.createTempDirectory("runbook-fixture-repo"); Files.writeString(repo.resolve("README.md"),"fixture"); command(repo,"git","init");command(repo,"git","config","user.email","test@example.invalid");command(repo,"git","config","user.name","Runbook Test");command(repo,"git","add",".");command(repo,"git","commit","-m","fixture");return repo.toString(); }
  private String git(String repo) throws Exception { return command(Path.of(repo),"git","rev-parse","HEAD"); }
  private String command(Path repo,String... args) throws Exception { Process p = new ProcessBuilder(args).directory(repo.toFile()).start(); if(p.waitFor()!=0)throw new IllegalStateException(new String(p.getErrorStream().readAllBytes()));return new String(p.getInputStream().readAllBytes()).trim(); }
}
