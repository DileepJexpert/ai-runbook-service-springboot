package com.idfc.ai.runbook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfc.ai.runbook.agent.FakeIdfcCoderExecutor;
import com.idfc.ai.runbook.agent.IdfcCoderExecutor;
import com.idfc.ai.runbook.agent.IdfcCoderResult;
import com.idfc.ai.runbook.agent.RunbookPromptBuilder;
import com.idfc.ai.runbook.api.dto.CreateJobRequest;
import com.idfc.ai.runbook.api.dto.PublishRequest;
import com.idfc.ai.runbook.artifact.BaselineStore;
import com.idfc.ai.runbook.artifact.RunbookArtifactStore;
import com.idfc.ai.runbook.config.RunbookProperties;
import com.idfc.ai.runbook.confluence.ConfluencePublisher;
import com.idfc.ai.runbook.diff.RunbookComparator;
import com.idfc.ai.runbook.normalization.RunbookNormalizer;
import com.idfc.ai.runbook.orchestration.*;
import com.idfc.ai.runbook.quality.QualityGateService;
import com.idfc.ai.runbook.rendering.ConfluenceRunbookRenderer;
import com.idfc.ai.runbook.rendering.MarkdownRunbookRenderer;
import com.idfc.ai.runbook.rendering.SupplementalArtifactRenderer;
import com.idfc.ai.runbook.repository.RemoteGitResolver;
import com.idfc.ai.runbook.repository.RepositoryWorkspaceProvider;
import com.idfc.ai.runbook.validation.RunbookEvidenceValidator;
import com.idfc.ai.runbook.validation.RunbookSafetyValidator;
import com.idfc.ai.runbook.validation.RunbookSchemaValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(FakeIdfcCoderExecutor.class)
class RunbookJobIntegrationTest {
  @Autowired RunbookJobService service;
  @MockBean RemoteGitResolver remoteGitResolver;

  @Autowired RunbookJobStore jobs;
  @Autowired RepositoryWorkspaceProvider workspace;
  @Autowired RunbookPromptBuilder prompt;
  @Autowired RunbookArtifactStore artifacts;
  @Autowired BaselineStore baseline;
  @Autowired RunbookSchemaValidator schema;
  @Autowired RunbookSafetyValidator safety;
  @Autowired RunbookEvidenceValidator evidence;
  @Autowired RunbookNormalizer normalizer;
  @Autowired RunbookComparator comparator;
  @Autowired MarkdownRunbookRenderer markdown;
  @Autowired ConfluenceRunbookRenderer html;
  @Autowired SupplementalArtifactRenderer supplemental;
  @Autowired ConfluencePublisher publisher;
  @Autowired QualityGateService quality;
  @Autowired ObjectMapper mapper;
  @Autowired RunbookProperties properties;

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

  @Test
  void template_first_extraction_initializes_fresh_templates_for_new_jobs() throws Exception {
    AtomicInteger callCount = new AtomicInteger(0);
    IdfcCoderExecutor templateCheckingAgent = request -> {
      callCount.incrementAndGet();
      try {
        Path dataFile = request.outputDirectory().resolve("runbook-data.json");
        Path evidenceFile = request.outputDirectory().resolve("runbook-evidence.json");
        Path securityFile = request.outputDirectory().resolve("security-findings.json");

        // 4. Fresh job receives all three templates before agent runs
        assertThat(Files.isRegularFile(dataFile)).isTrue();
        assertThat(Files.isRegularFile(evidenceFile)).isTrue();
        assertThat(Files.isRegularFile(securityFile)).isTrue();

        // 6. Templates contain no previous-service facts
        String initialData = Files.readString(dataFile);
        assertThat(initialData).contains("<SERVICE_NAME>", "<BUSINESS_PURPOSE>");
        assertThat(initialData).doesNotContain("payments-integration-services");

        // Populate valid facts into the template
        Files.copy(Path.of("src/test/resources/fixtures/runbook-data.json"), dataFile, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Path.of("src/test/resources/fixtures/runbook-evidence.json"), evidenceFile, StandardCopyOption.REPLACE_EXISTING);
        return new IdfcCoderResult("template filled", "");
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    };

    RunbookJobService templateService = new RunbookJobService(jobs, workspace, templateCheckingAgent, prompt, artifacts, baseline, schema, safety, evidence, normalizer, comparator, markdown, html, supplemental, publisher, quality, mapper, properties);

    String repo = temporaryGitRepository();
    String sha = git(repo);
    var request = new CreateJobRequest("template-test-service", new CreateJobRequest.Repository("LOCAL_PATH", repo, sha), new CreateJobRequest.Deployment("TEST"));

    RunbookJob job = templateService.create(request);
    for (int i = 0; i < 400 && job.state != RunbookJobState.READY_TO_PUBLISH && job.state != RunbookJobState.FAILED; i++) Thread.sleep(25);

    // 19. idfc-coder invoked exactly once
    assertThat(callCount.get()).isEqualTo(1);
    assertThat(job.state).isEqualTo(RunbookJobState.READY_TO_PUBLISH);
  }

  @Test
  void invalid_extracted_schema_fails_with_runbook_schema_invalid_in_single_analysis() throws Exception {
    AtomicInteger callCount = new AtomicInteger(0);
    IdfcCoderExecutor failingAgent = request -> {
      callCount.incrementAndGet();
      try {
        Files.writeString(request.outputDirectory().resolve("runbook-data.json"), "{\"contractVersion\":\"2.1\",\"serviceName\":\"test-svc\"}");
        Files.writeString(request.outputDirectory().resolve("runbook-evidence.json"), "{\"contractVersion\":\"2.1\",\"serviceName\":\"test-svc\"}");
        Files.writeString(request.outputDirectory().resolve("security-findings.json"), "{\"contractVersion\":\"2.1\",\"findings\":[]}\n");
        return new IdfcCoderResult("attempt " + callCount.get(), "");
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    };

    RunbookJobService failingService = new RunbookJobService(jobs, workspace, failingAgent, prompt, artifacts, baseline, schema, safety, evidence, normalizer, comparator, markdown, html, supplemental, publisher, quality, mapper, properties);

    String repo = temporaryGitRepository();
    String sha = git(repo);
    var request = new CreateJobRequest("single-pass-failing-service", new CreateJobRequest.Repository("LOCAL_PATH", repo, sha), new CreateJobRequest.Deployment("TEST"));

    RunbookJob job = failingService.create(request);
    for (int i = 0; i < 400 && job.state != RunbookJobState.READY_TO_PUBLISH && job.state != RunbookJobState.FAILED; i++) Thread.sleep(25);

    // Exactly 1 analysis, no retry loop
    assertThat(callCount.get()).isEqualTo(1);
    assertThat(job.state).isEqualTo(RunbookJobState.FAILED);
    assertThat(job.failureCode).isEqualTo("RUNBOOK_SCHEMA_INVALID");
  }

  @Test
  void pregenerated_files_mode_does_not_overwrite_supplied_artifacts() throws Exception {
    String repo = temporaryGitRepository();
    String sha = git(repo);

    Path customData = Files.createTempFile("pregen-data", ".json");
    Path customEv = Files.createTempFile("pregen-ev", ".json");
    Path customSec = Files.createTempFile("pregen-sec", ".json");

    Files.copy(Path.of("src/test/resources/fixtures/runbook-data.json"), customData, StandardCopyOption.REPLACE_EXISTING);
    Files.copy(Path.of("src/test/resources/fixtures/runbook-evidence.json"), customEv, StandardCopyOption.REPLACE_EXISTING);
    Files.writeString(customSec, "{\"contractVersion\":\"2.1\",\"findings\":[]}\n");

    var request = new CreateJobRequest(
        "pregen-service",
        new CreateJobRequest.Repository("LOCAL_PATH", repo, sha),
        new CreateJobRequest.Deployment("TEST"),
        new CreateJobRequest.ExtractionInput("PREGENERATED_FILES", customData.toString(), customEv.toString(), customSec.toString())
    );

    RunbookJob job = service.create(request);
    for (int i = 0; i < 400 && job.state != RunbookJobState.READY_TO_PUBLISH && job.state != RunbookJobState.FAILED; i++) Thread.sleep(25);

    assertThat(job.state).withFailMessage("state=%s failure=%s: %s", job.state, job.failureCode, job.failureMessage).isEqualTo(RunbookJobState.READY_TO_PUBLISH);
    Path root = Path.of(job.artifacts.get("root"));
    assertThat(root.resolve("extraction/runbook-data.json")).isRegularFile();
  }

  private String temporaryGitRepository() throws Exception { Path repo=Files.createTempDirectory("runbook-fixture-repo"); Files.writeString(repo.resolve("README.md"),"fixture"); command(repo,"git","init");command(repo,"git","config","user.email","test@example.invalid");command(repo,"git","config","user.name","Runbook Test");command(repo,"git","add",".");command(repo,"git","commit","-m","fixture");return repo.toString(); }
  private String git(String repo) throws Exception { return command(Path.of(repo),"git","rev-parse","HEAD"); }
  private String command(Path repo,String... args) throws Exception { Process p = new ProcessBuilder(args).directory(repo.toFile()).start(); if(p.waitFor()!=0)throw new IllegalStateException(new String(p.getErrorStream().readAllBytes()));return new String(p.getInputStream().readAllBytes()).trim(); }
}
