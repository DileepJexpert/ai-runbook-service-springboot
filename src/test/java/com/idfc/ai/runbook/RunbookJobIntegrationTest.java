package com.idfc.ai.runbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfc.ai.runbook.agent.FakeIdfcCoderExecutor;
import com.idfc.ai.runbook.agent.IdfcCoderExecutor;
import com.idfc.ai.runbook.agent.IdfcCoderResult;
import com.idfc.ai.runbook.agent.LeanRunbookPromptBuilder;
import com.idfc.ai.runbook.agent.RunbookPromptBuilder;
import com.idfc.ai.runbook.api.dto.CreateJobRequest;
import com.idfc.ai.runbook.api.dto.PublishRequest;
import com.idfc.ai.runbook.artifact.BaselineStore;
import com.idfc.ai.runbook.artifact.RunbookArtifactStore;
import com.idfc.ai.runbook.client.FakeRunbookAiClient;
import com.idfc.ai.runbook.client.RunbookAiClient;
import com.idfc.ai.runbook.collector.RepositoryContextCollector;
import com.idfc.ai.runbook.config.RunbookProperties;
import com.idfc.ai.runbook.confluence.ConfluencePublisher;
import com.idfc.ai.runbook.diff.RunbookComparator;
import com.idfc.ai.runbook.normalization.RunbookNormalizer;
import com.idfc.ai.runbook.orchestration.*;
import com.idfc.ai.runbook.quality.QualityGateService;
import com.idfc.ai.runbook.rendering.ConfluenceRunbookRenderer;
import com.idfc.ai.runbook.rendering.LeanMarkdownToHtmlConverter;
import com.idfc.ai.runbook.rendering.MarkdownRunbookRenderer;
import com.idfc.ai.runbook.rendering.SupplementalArtifactRenderer;
import com.idfc.ai.runbook.repository.RemoteGitResolver;
import com.idfc.ai.runbook.repository.RepositoryWorkspaceProvider;
import com.idfc.ai.runbook.validation.LeanRunbookValidator;
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
@Import({FakeIdfcCoderExecutor.class, FakeRunbookAiClient.class})
class RunbookJobIntegrationTest {
  @Autowired RunbookJobService service;
  @MockBean RemoteGitResolver remoteGitResolver;

  @Autowired RunbookJobStore jobs;
  @Autowired RepositoryWorkspaceProvider workspace;
  @Autowired IdfcCoderExecutor agent;
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

  @Autowired RepositoryContextCollector collector;
  @Autowired LeanRunbookPromptBuilder leanPromptBuilder;
  @Autowired RunbookAiClient aiClient;
  @Autowired LeanMarkdownToHtmlConverter markdownToHtml;
  @Autowired LeanRunbookValidator leanValidator;

  @Test void default_mode_is_lean_in_properties() {
    assertThat(properties.getGeneration().getMode()).isEqualTo("LEAN");
    assertThat(properties.getGeneration().isLean()).isTrue();
  }

  @Test void lean_mode_local_path_generates_and_publishes() throws Exception {
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
    assertThat(root.resolve("render/RUNBOOK.md")).isRegularFile();
    assertThat(root.resolve("render/confluence-body.html")).isRegularFile();
    // LEAN mode does not create structured intermediate files
    assertThat(root.resolve("extraction/runbook-data.json")).doesNotExist();

    service.publish(job.id, new PublishRequest("TEST", sha, "payments:1.0"));
    assertThat(job.state).isEqualTo(RunbookJobState.PUBLISHED);
  }

  @Test void lean_mode_bitbucket_generates_runbook_using_remote_resolution() throws Exception {
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
  void lean_mode_does_not_invoke_idfc_coder_executor() throws Exception {
    AtomicInteger agentCalls = new AtomicInteger(0);
    IdfcCoderExecutor spyAgent = request -> {
      agentCalls.incrementAndGet();
      return new IdfcCoderResult("unexpected call", "");
    };

    RunbookProperties leanProps = new RunbookProperties();
    leanProps.getGeneration().setMode("LEAN");

    RunbookJobService leanService = new RunbookJobService(
        jobs, workspace, spyAgent, prompt, artifacts, baseline,
        schema, safety, evidence, normalizer, comparator, markdown, html,
        supplemental, publisher, quality, mapper, leanProps,
        collector, leanPromptBuilder, aiClient, markdownToHtml, leanValidator
    );

    String repo = temporaryGitRepository();
    String sha = git(repo);
    var request = new CreateJobRequest("lean-no-agent-service", new CreateJobRequest.Repository("LOCAL_PATH", repo, sha), new CreateJobRequest.Deployment("TEST"));

    RunbookJob job = leanService.create(request);
    for (int i = 0; i < 400 && job.state != RunbookJobState.READY_TO_PUBLISH && job.state != RunbookJobState.FAILED; i++) Thread.sleep(25);

    assertThat(job.state).isEqualTo(RunbookJobState.READY_TO_PUBLISH);
    // Verified: idfc-coder executor is NEVER called in LEAN mode
    assertThat(agentCalls.get()).isEqualTo(0);
  }

  @Test
  void missing_idfc_coder_binary_does_not_affect_lean_mode() throws Exception {
    RunbookProperties leanProps = new RunbookProperties();
    leanProps.getGeneration().setMode("LEAN");
    // Configure a completely non-existent executable path for idfc-coder
    leanProps.getAgent().setExecutable("/usr/local/bin/non-existent-idfc-coder-binary-9999");

    RunbookJobService leanService = new RunbookJobService(
        jobs, workspace, agent, prompt, artifacts, baseline,
        schema, safety, evidence, normalizer, comparator, markdown, html,
        supplemental, publisher, quality, mapper, leanProps,
        collector, leanPromptBuilder, aiClient, markdownToHtml, leanValidator
    );

    String repo = temporaryGitRepository();
    String sha = git(repo);
    var request = new CreateJobRequest("lean-missing-binary-service", new CreateJobRequest.Repository("LOCAL_PATH", repo, sha), new CreateJobRequest.Deployment("TEST"));

    RunbookJob job = leanService.create(request);
    for (int i = 0; i < 400 && job.state != RunbookJobState.READY_TO_PUBLISH && job.state != RunbookJobState.FAILED; i++) Thread.sleep(25);

    assertThat(job.state).isEqualTo(RunbookJobState.READY_TO_PUBLISH);
  }

  @Test
  void lean_mode_ai_failure_fails_clearly_and_does_not_fallback_to_idfc_coder() throws Exception {
    AtomicInteger agentCalls = new AtomicInteger(0);
    IdfcCoderExecutor spyAgent = request -> {
      agentCalls.incrementAndGet();
      return new IdfcCoderResult("fallback call", "");
    };

    RunbookAiClient failingAiClient = promptText -> {
      throw new IllegalStateException("RUNBOOK_AGENT_FAILED: LLM API returned HTTP 500");
    };

    RunbookProperties leanProps = new RunbookProperties();
    leanProps.getGeneration().setMode("LEAN");

    RunbookJobService leanService = new RunbookJobService(
        jobs, workspace, spyAgent, prompt, artifacts, baseline,
        schema, safety, evidence, normalizer, comparator, markdown, html,
        supplemental, publisher, quality, mapper, leanProps,
        collector, leanPromptBuilder, failingAiClient, markdownToHtml, leanValidator
    );

    String repo = temporaryGitRepository();
    String sha = git(repo);
    var request = new CreateJobRequest("lean-failing-ai-service", new CreateJobRequest.Repository("LOCAL_PATH", repo, sha), new CreateJobRequest.Deployment("TEST"));

    RunbookJob job = leanService.create(request);
    for (int i = 0; i < 400 && job.state != RunbookJobState.READY_TO_PUBLISH && job.state != RunbookJobState.FAILED; i++) Thread.sleep(25);

    assertThat(job.state).isEqualTo(RunbookJobState.FAILED);
    assertThat(job.failureCode).isEqualTo("RUNBOOK_AGENT_FAILED");
    // Proves there is NO silent fallback to idfc-coder
    assertThat(agentCalls.get()).isEqualTo(0);
  }

  @Test
  void structured_mode_template_first_extraction_initializes_fresh_templates() throws Exception {
    AtomicInteger callCount = new AtomicInteger(0);
    IdfcCoderExecutor templateCheckingAgent = request -> {
      callCount.incrementAndGet();
      try {
        Path dataFile = request.outputDirectory().resolve("runbook-data.json");
        Path evidenceFile = request.outputDirectory().resolve("runbook-evidence.json");
        Path securityFile = request.outputDirectory().resolve("security-findings.json");

        assertThat(Files.isRegularFile(dataFile)).isTrue();
        assertThat(Files.isRegularFile(evidenceFile)).isTrue();
        assertThat(Files.isRegularFile(securityFile)).isTrue();

        String initialData = Files.readString(dataFile);
        assertThat(initialData).contains("<SERVICE_NAME>", "<BUSINESS_PURPOSE>");

        Files.copy(Path.of("src/test/resources/fixtures/runbook-data.json"), dataFile, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Path.of("src/test/resources/fixtures/runbook-evidence.json"), evidenceFile, StandardCopyOption.REPLACE_EXISTING);
        return new IdfcCoderResult("template filled", "");
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    };

    RunbookProperties structuredProps = new RunbookProperties();
    structuredProps.getGeneration().setMode("STRUCTURED");

    RunbookJobService structuredService = new RunbookJobService(
        jobs, workspace, templateCheckingAgent, prompt, artifacts, baseline,
        schema, safety, evidence, normalizer, comparator, markdown, html,
        supplemental, publisher, quality, mapper, structuredProps,
        collector, leanPromptBuilder, aiClient, markdownToHtml, leanValidator
    );

    String repo = temporaryGitRepository();
    String sha = git(repo);
    var request = new CreateJobRequest("structured-template-service", new CreateJobRequest.Repository("LOCAL_PATH", repo, sha), new CreateJobRequest.Deployment("TEST"));

    RunbookJob job = structuredService.create(request);
    for (int i = 0; i < 400 && job.state != RunbookJobState.READY_TO_PUBLISH && job.state != RunbookJobState.FAILED; i++) Thread.sleep(25);

    assertThat(callCount.get()).isEqualTo(1);
    assertThat(job.state).isEqualTo(RunbookJobState.READY_TO_PUBLISH);
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
