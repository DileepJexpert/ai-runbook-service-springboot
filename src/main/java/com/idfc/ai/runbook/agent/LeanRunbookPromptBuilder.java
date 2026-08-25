package com.idfc.ai.runbook.agent;

import com.idfc.ai.runbook.rendering.RunbookSection;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LeanRunbookPromptBuilder {

  public static final List<String> FROZEN_SECTIONS = List.of(
      "1. Service Overview & Criticality",
      "2. Quick Support Summary",
      "3. Business Flow & Decision Guide",
      "4. API / Event Contract & Validation",
      "5. Business Decision Rules",
      "6. Response & Error Mapping",
      "7. Data Origin, Reference Data & Transformation",
      "8. Transaction Lifecycle, States & Recovery",
      "9. Downstream Dependencies & Response Interpretation",
      "10. Datastore Support Evidence",
      "11. Kafka & Asynchronous Processing",
      "12. Resilience & Automatic Failure Handling",
      "13. Rate Limiting, Capacity & Concurrency",
      "14. Data Retention, Expiry & Archival",
      "15. Idempotency & Duplicate Protection",
      "16. Deployment / Schema Compatibility",
      "17. Operational Errors & Troubleshooting",
      "18. Alerts / Support Health Checks & Monitoring",
      "19. Transaction Tracing",
      "20. Unknown / Unclassified Incident Triage",
      "21. Support Responsibility & Access",
      "22. Escalation Evidence Checklist",
      "23. Pipeline & Generation Metadata"
  );

  public String buildPrompt(String serviceId, String repoMode, String repoUrl, String commitSha, String repoContext) {
    StringBuilder sb = new StringBuilder();

    sb.append("You are an expert Production Support Runbook Engineer for IDFC FIRST Bank.\n");
    sb.append("Generate a comprehensive, authoritative Production Support Runbook in Markdown format for the target microservice based strictly on the provided repository context.\n\n");

    sb.append("============================================================\n");
    sb.append("TARGET SERVICE METADATA\n");
    sb.append("============================================================\n");
    sb.append("- Service ID: ").append(serviceId != null ? serviceId : "unknown-service").append("\n");
    sb.append("- Repository Mode: ").append(repoMode != null ? repoMode : "LOCAL").append("\n");
    if (repoUrl != null && !repoUrl.isBlank()) {
      sb.append("- Repository URL: ").append(repoUrl).append("\n");
    }
    sb.append("- Analyzed Git Commit SHA: ").append(commitSha != null ? commitSha : "HEAD").append("\n\n");

    sb.append("============================================================\n");
    sb.append("GROUNDING & REPOSITORY-EVIDENCE RULES\n");
    sb.append("============================================================\n");
    sb.append("1. Base all facts, flows, tables, topics, configurations, and troubleshooting steps ONLY on the provided repository context.\n");
    sb.append("2. DO NOT invent fictitious endpoints, database tables, Kafka topics, queues, timeouts, error codes, owners, or support contacts.\n");
    sb.append("3. If evidence for any section or concept is not found in the repository, explicitly state: 'Not found in repository'.\n");
    sb.append("4. If a configuration property key is known from code/yaml but its runtime value is externalized in Config Server/Vault, state: 'Check Config Portal' (or 'Protected - check Config Portal' for sensitive keys).\n");
    sb.append("5. Where practical, cite source class names or configuration files for critical facts.\n\n");

    sb.append("============================================================\n");
    sb.append("PRODUCTION SUPPORT SAFETY RULES (CRITICAL / NON-NEGOTIABLE)\n");
    sb.append("============================================================\n");
    sb.append("All Support troubleshooting actions, resolution steps, and verification instructions must be strictly READ-ONLY.\n");
    sb.append("You must NEVER instruct Support or Operations to:\n");
    sb.append("- Manually mutate, insert, update, or delete production database or Aerospike records.\n");
    sb.append("- Replay Kafka events or republish messages manually.\n");
    sb.append("- Alter, reset, rewind, advance, or seek Kafka consumer offsets.\n");
    sb.append("- Manually reprocess payments, transactions, or requests.\n");
    sb.append("- Manually force, trigger, or run schedulers, cron jobs, or background workers.\n");
    sb.append("- Modify production configuration files, properties, or secrets.\n");
    sb.append("- Perform manual state-changing reconciliation.\n");
    sb.append("- Restart or scale pods, containers, or services as a recovery mechanism.\n\n");

    sb.append("============================================================\n");
    sb.append("REQUIRED DOCUMENT STRUCTURE (EXACT 23 FROZEN SECTIONS)\n");
    sb.append("============================================================\n");
    sb.append("The generated runbook MUST start with the title `# Production Support Runbook — ").append(serviceId).append("` and contain the following 23 sections as `## ` level-2 headings in this exact order:\n\n");

    for (String section : FROZEN_SECTIONS) {
      sb.append("## ").append(section).append("\n");
    }

    sb.append("\nSection specific expectations:\n");
    sb.append("- Section 1 (Service Overview & Criticality): Business purpose, business owner, support owner, criticality (HIGH/MEDIUM/LOW), escalation channel.\n");
    sb.append("- Section 2 (Quick Support Summary): High-level processing summary, key support pointers.\n");
    sb.append("- Section 3 (Business Flow & Decision Guide): End-to-end request lifecycle, trigger conditions, step-by-step branch decisions, outcomes.\n");
    sb.append("- Section 4 (API / Event Contract & Validation): Table of endpoints (Path/Event, Method, Auth, Processing Model, Validation Summary, Success Meaning).\n");
    sb.append("- Section 5 (Business Decision Rules): Table of business rules (Condition, Data Used, Result/Response, Support Check).\n");
    sb.append("- Section 6 (Response & Error Mapping): Table of errors (Error Code / Status, Result, Possible Causes, How to Confirm, Support Action).\n");
    sb.append("- Section 7 (Data Origin, Reference Data & Transformation): Sources of reference data, caches, transformation mappings.\n");
    sb.append("- Section 8 (Transaction Lifecycle, States & Recovery): Transaction status transitions (fromState, toState, trigger, recovery action).\n");
    sb.append("- Section 9 (Downstream Dependencies & Response Interpretation): Table of external services (Dependency, Purpose, Timeout, Automatic Failure Handling, Failure Propagation, Support Check).\n");
    sb.append("- Section 10 (Datastore Support Evidence): Database / Aerospike tables, lookup keys, access mode, support fields.\n");
    sb.append("- Section 11 (Kafka & Asynchronous Processing): Inbound/outbound topics, consumer groups, dead-letter topics, failure handling.\n");
    sb.append("- Section 12 (Resilience & Automatic Failure Handling): Circuit breakers, retries, rate limiters, fallback mechanisms.\n");
    sb.append("- Section 13 (Rate Limiting, Capacity & Concurrency): Thread pools, connection pools, throttling limits.\n");
    sb.append("- Section 14 (Data Retention, Expiry & Archival): TTLs, cleanup jobs, archive destinations.\n");
    sb.append("- Section 15 (Idempotency & Duplicate Protection): Deduplication keys, idempotency headers, duplicate handling.\n");
    sb.append("- Section 16 (Deployment / Schema Compatibility): Flyway/Liquibase migrations, backward compatibility notes, feature flags.\n");
    sb.append("- Section 17 (Operational Errors & Troubleshooting): Log signature patterns, error codes, triage and diagnostic verification.\n");
    sb.append("- Section 18 (Alerts / Support Health Checks & Monitoring): Actuator health endpoints, metrics, configured alert thresholds.\n");
    sb.append("- Section 19 (Transaction Tracing): Trace headers (e.g. X-Correlation-ID), MDC log keys.\n");
    sb.append("- Section 20 (Unknown / Unclassified Incident Triage): Step-by-step diagnostic workflow when error is unclassified.\n");
    sb.append("- Section 21 (Support Responsibility & Access): Standard boundary statement: 'L1/L2 do not replay events, change offsets, mutate production data, or change runtime/configuration.'\n");
    sb.append("- Section 22 (Escalation Evidence Checklist): Checklist of logs, query outputs, and payloads to attach when escalating to L3/Engineering.\n");
    sb.append("- Section 23 (Pipeline & Generation Metadata): Git commit, generation timestamp, generation mode (LEAN).\n\n");

    sb.append("============================================================\n");
    sb.append("OUTPUT FORMAT INSTRUCTIONS\n");
    sb.append("============================================================\n");
    sb.append("Return raw Markdown text ONLY. Do NOT wrap the entire response in triple backticks (```markdown ... ```). Do NOT include conversational greetings.\n\n");

    sb.append("============================================================\n");
    sb.append("COLLECTED REPOSITORY CONTEXT\n");
    sb.append("============================================================\n");
    sb.append(repoContext != null ? repoContext : "(No repository files collected)");

    return sb.toString();
  }
}
