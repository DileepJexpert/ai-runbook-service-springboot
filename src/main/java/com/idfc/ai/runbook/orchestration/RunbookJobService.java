package com.idfc.ai.runbook.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfc.ai.runbook.agent.*;
import com.idfc.ai.runbook.api.dto.*;
import com.idfc.ai.runbook.artifact.*;
import com.idfc.ai.runbook.config.RunbookProperties;
import com.idfc.ai.runbook.confluence.ConfluencePublisher;
import com.idfc.ai.runbook.diff.*;
import com.idfc.ai.runbook.normalization.RunbookNormalizer;
import com.idfc.ai.runbook.quality.*;
import com.idfc.ai.runbook.rendering.*;
import com.idfc.ai.runbook.repository.RepositoryWorkspaceProvider;
import com.idfc.ai.runbook.validation.*;
import java.nio.file.Path;
import java.util.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class RunbookJobService {
  private final RunbookJobStore jobs;
  private final RepositoryWorkspaceProvider workspace;
  private final IdfcCoderExecutor agent;
  private final RunbookPromptBuilder prompt;
  private final RunbookArtifactStore artifacts;
  private final BaselineStore baseline;
  private final RunbookSchemaValidator schema;
  private final RunbookSafetyValidator safety;
  private final RunbookEvidenceValidator evidence;
  private final RunbookNormalizer normalizer;
  private final RunbookComparator comparator;
  private final MarkdownRunbookRenderer markdown;
  private final ConfluenceRunbookRenderer html;
  private final SupplementalArtifactRenderer supplemental;
  private final ConfluencePublisher publisher;
  private final QualityGateService quality;
  private final ObjectMapper mapper;
  private final TaskExecutor executor;

  public RunbookJobService(RunbookJobStore jobs, RepositoryWorkspaceProvider workspace, IdfcCoderExecutor agent, RunbookPromptBuilder prompt, RunbookArtifactStore artifacts, BaselineStore baseline, RunbookSchemaValidator schema, RunbookSafetyValidator safety, RunbookEvidenceValidator evidence, RunbookNormalizer normalizer, RunbookComparator comparator, MarkdownRunbookRenderer markdown, ConfluenceRunbookRenderer html, SupplementalArtifactRenderer supplemental, ConfluencePublisher publisher, QualityGateService quality, ObjectMapper mapper, RunbookProperties properties) {
    this.jobs = jobs;
    this.workspace = workspace;
    this.agent = agent;
    this.prompt = prompt;
    this.artifacts = artifacts;
    this.baseline = baseline;
    this.schema = schema;
    this.safety = safety;
    this.evidence = evidence;
    this.normalizer = normalizer;
    this.comparator = comparator;
    this.markdown = markdown;
    this.html = html;
    this.supplemental = supplemental;
    this.publisher = publisher;
    this.quality = quality;
    this.mapper = mapper;
    ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
    pool.setCorePoolSize(properties.getExecutor().getCorePoolSize());
    pool.setMaxPoolSize(properties.getExecutor().getMaxPoolSize());
    pool.setQueueCapacity(properties.getExecutor().getQueueCapacity());
    pool.initialize();
    executor = pool;
  }

  public RunbookJob create(CreateJobRequest request) {
    if (request.repository() == null || request.repository().mode() == null) {
      throw new IllegalArgumentException("RUNBOOK_REPOSITORY_INVALID: missing repository configuration");
    }
    String mode = request.repository().mode().toUpperCase();
    if (!"LOCAL_PATH".equals(mode) && !"BITBUCKET".equals(mode)) {
      throw new IllegalArgumentException("RUNBOOK_REPOSITORY_INVALID: unsupported repository mode " + request.repository().mode());
    }
    if (request.extractionInput() != null && !"PREGENERATED_FILES".equals(request.extractionInput().mode())) {
      throw new IllegalArgumentException("RUNBOOK_FILE_AGENT_INVALID");
    }
    RunbookJob job = jobs.save(new RunbookJob(request));
    executor.execute(() -> run(job));
    return job;
  }

  private void run(RunbookJob job) {
    try {
      job.transition(RunbookJobState.PREPARING_WORKSPACE);
      var checkout = workspace.prepare(job.repo, job.requestedCommit);
      if (job.requestedCommit == null || job.requestedCommit.isBlank()) {
        job.requestedCommit = checkout.commitSha();
      }
      Path root = artifacts.jobRoot(job.serviceId, job.id);
      job.artifacts.put("root", root.toString());

      job.transition(RunbookJobState.EXTRACTING);
      var input = job.extractionInput;
      var idfcRequest = new IdfcCoderRequest(
          checkout.path(),
          root.resolve("extraction"),
          prompt.prompt(),
          prompt.context(),
          input == null ? null : (input.dataPath() != null ? Path.of(input.dataPath()) : null),
          input == null ? null : (input.evidencePath() != null ? Path.of(input.evidencePath()) : null),
          input == null ? null : (input.securityFindingsPath() != null ? Path.of(input.securityFindingsPath()) : null),
          checkout.mode(),
          checkout.url(),
          checkout.branch(),
          checkout.commitSha()
      );
      var result = agent.execute(idfcRequest);
      artifacts.write(root, "extraction/idfc-coder.stdout.log", result.stdout());
      artifacts.write(root, "extraction/idfc-coder.stderr.log", result.stderr());

      JsonNode data = mapper.readTree(artifacts.read(root, "extraction/runbook-data.json"));
      JsonNode ev = mapper.readTree(artifacts.read(root, "extraction/runbook-evidence.json"));

      String extractedCommit = data.path("pipeline").path("gitCommitSha").asText();
      if (!extractedCommit.isBlank() && job.requestedCommit != null && !job.requestedCommit.isBlank()) {
        if (!extractedCommit.equalsIgnoreCase(job.requestedCommit) && !extractedCommit.startsWith(job.requestedCommit) && !job.requestedCommit.startsWith(extractedCommit)) {
          throw new IllegalArgumentException("RUNBOOK_COMMIT_MISMATCH: extracted commit " + extractedCommit + " does not match requested " + job.requestedCommit);
        }
      }
      job.analyzedCommit = !extractedCommit.isBlank() ? extractedCommit : checkout.commitSha();

      job.transition(RunbookJobState.VALIDATING);
      List<String> errors = validationErrors(data, ev);
      QualityGateDecision gate = quality.evaluate(data, errors);
      artifacts.write(root, "validation/validation-report.json", mapper.writeValueAsString(Map.of("valid", errors.isEmpty(), "errors", errors, "qualityGate", gate.outcome())));
      if (!errors.isEmpty()) throw new IllegalArgumentException("RUNBOOK_SCHEMA_INVALID: " + String.join(",", errors));

      job.transition(RunbookJobState.NORMALIZING);
      JsonNode normalized = normalizer.normalize(data);
      artifacts.write(root, "normalized/normalized-runbook-data.json", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(normalized));

      job.transition(RunbookJobState.DIFFING);
      JsonNode previous = baseline.loadLatestSuccessful(job.serviceId, job.environment).map(this::json).orElse(null);
      OperationalDiff delta = comparator.compare(normalized, previous);
      job.operationalChange = delta.hasOperationalChanges();
      job.changedSections.addAll(delta.changedSections());
      artifacts.write(root, "diff/operational-diff.json", mapper.writeValueAsString(delta));
      artifacts.write(root, "diff/runbook-delta.json", mapper.writeValueAsString(RunbookDeltaArtifact.create(previous == null ? null : job.serviceId + ":" + job.environment, job.serviceId + ":" + job.analyzedCommit, delta)));

      if (!delta.hasOperationalChanges()) {
        renderSupplemental(root, normalized, delta);
        report(root, job, data, gate);
        job.transition(RunbookJobState.NO_OPERATIONAL_CHANGE);
        return;
      }

      job.transition(RunbookJobState.RENDERING);
      artifacts.write(root, "render/RUNBOOK.md", markdown.render(normalized));
      artifacts.write(root, "render/confluence-body.html", html.render(normalized));
      renderSupplemental(root, normalized, delta);
      report(root, job, data, gate);
      job.transition(gate.publishEligible() ? RunbookJobState.READY_TO_PUBLISH : RunbookJobState.RENDERED_PUBLISH_BLOCKED);
    } catch (Exception exception) {
      job.fail(code(exception), exception.getMessage());
    }
  }

  private List<String> validationErrors(JsonNode data, JsonNode ev) {
    List<String> errors = new ArrayList<>();
    for (ValidationResult value : List.of(schema.validate(data, ev), safety.validate(data), evidence.validate(ev))) {
      errors.addAll(value.errors());
    }
    return errors;
  }

  private JsonNode json(String value) {
    try {
      return mapper.readTree(value);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void renderSupplemental(Path root, JsonNode data, OperationalDiff delta) {
    supplemental.render(data, delta).forEach((name, body) -> artifacts.write(root, "render/" + name, body));
  }

  private void report(Path root, RunbookJob job, JsonNode data, QualityGateDecision gate) throws Exception {
    GenerationReportWriter.write(mapper, artifacts, root, job, data, gate, prompt.promptFingerprint());
  }

  public RunbookJob publish(UUID id, PublishRequest request) {
    RunbookJob job = get(id);
    if (job.state != RunbookJobState.READY_TO_PUBLISH) {
      throw new IllegalStateException("RUNBOOK_PUBLISH_FAILED: invalid state");
    }
    if (!Objects.equals(job.analyzedCommit, request.deployedCommitSha()) || (request.deployedImageTag() != null && !request.deployedImageTag().equals(job.imageTag))) {
      throw new IllegalArgumentException("RUNBOOK_DEPLOYED_COMMIT_MISMATCH");
    }
    job.transition(RunbookJobState.PUBLISHING);
    try {
      Path root = Path.of(job.artifacts.get("root"));
      publisher.publish(job.serviceId, request.mode(), artifacts.read(root, "render/confluence-body.html"));
      baseline.saveSuccessful(job.serviceId, job.environment, artifacts.read(root, "normalized/normalized-runbook-data.json"));
      job.transition(RunbookJobState.PUBLISHED);
      return job;
    } catch (Exception exception) {
      job.fail(code(exception), exception.getMessage());
      throw exception;
    }
  }

  public RunbookJob get(UUID id) {
    return jobs.get(id).orElseThrow(() -> new NoSuchElementException("RUNBOOK_JOB_NOT_FOUND"));
  }

  private String code(Exception exception) {
    String message = exception.getMessage();
    return message != null && message.startsWith("RUNBOOK_") ? message.split("[: ]")[0] : "RUNBOOK_AGENT_FAILED";
  }
}
