package com.idfc.ai.runbook.agent;

import com.idfc.ai.runbook.config.RunbookProperties;
import org.springframework.stereotype.Component;

@Component
public class DirectStructuredRunbookPromptBuilder {

  private final RunbookPromptBuilder promptBuilder;
  private final RunbookProperties properties;

  public DirectStructuredRunbookPromptBuilder(RunbookPromptBuilder promptBuilder, RunbookProperties properties) {
    this.promptBuilder = promptBuilder;
    this.properties = properties;
  }

  public String buildPrompt(String serviceId, String repoMode, String repoUrl, String branch, String commitSha, String repoContext) {
    StringBuilder sb = new StringBuilder();

    sb.append("You are an expert Production Support Runbook Engineer for IDFC FIRST Bank.\n");
    sb.append("Extract structured repository facts from the provided source code context into canonical JSON format.\n\n");

    sb.append("============================================================\n");
    sb.append("TARGET REPOSITORY METADATA\n");
    sb.append("============================================================\n");
    sb.append("- Service ID: ").append(serviceId != null ? serviceId : "unknown-service").append("\n");
    sb.append("- Repository Mode: ").append(repoMode != null ? repoMode : "LOCAL").append("\n");
    if (repoUrl != null && !repoUrl.isBlank()) {
      sb.append("- Repository URL: ").append(repoUrl).append("\n");
    }
    if (branch != null && !branch.isBlank()) {
      sb.append("- Branch: ").append(branch).append("\n");
    }
    sb.append("- Analyzed Git Commit SHA: ").append(commitSha != null ? commitSha : "HEAD").append("\n\n");

    sb.append("============================================================\n");
    sb.append("OUTPUT FORMAT CONTRACT (CRITICAL / NON-NEGOTIABLE)\n");
    sb.append("============================================================\n");
    sb.append("You must return exactly ONE valid JSON object envelope containing all three canonical documents.\n");
    sb.append("Do NOT wrap the output in Markdown code blocks (e.g. no ```json or ```).\n");
    sb.append("Do NOT include any commentary, explanations, greetings, or prose before or after the JSON.\n\n");
    sb.append("The root transport envelope MUST have this exact JSON shape:\n");
    sb.append("{\n");
    sb.append("  \"runbookData\": { ... populated runbook-data document conforming to Service Intelligence contract 2.1 ... },\n");
    sb.append("  \"runbookEvidence\": { ... populated runbook-evidence document conforming to contract 2.1 ... },\n");
    sb.append("  \"securityFindings\": { ... populated security-findings document conforming to contract 2.1 ... }\n");
    sb.append("}\n\n");

    sb.append("============================================================\n");
    sb.append("MANDATORY SCHEMA & STRUCTURE RULES\n");
    sb.append("============================================================\n");
    sb.append("1. 'runbookData':\n");
    sb.append("   - 'contractVersion': \"2.1\"\n");
    sb.append("   - 'schemaVersion': \"2.1\"\n");
    sb.append("   - 'generator.scanStatus': \"COMPLETE\"\n");
    sb.append("   - 'service.name': \"").append(serviceId).append("\"\n");
    sb.append("   - 'service': { \"name\": \"").append(serviceId).append("\", \"businessPurpose\": \"...\", \"businessCriticality\": \"HIGH|MEDIUM|LOW\" }\n");
    sb.append("   - 'pipeline': { \"gitCommitSha\": \"").append(commitSha != null ? commitSha : "").append("\" }\n");
    sb.append("   - Populates canonical sections: apis, businessFlows, businessRules, responseMappings, dataLineage, stateTransitions, downstreamDependencies, databaseTables, aerospike, kafkaConsumers, kafkaProducers, resilience, rateLimits, retentionRules, duplicateProtection, migrationRisks, logSignatures, traceIdentifiers, supportHealthChecks, unknownIncidentContext, supportOwnership, escalationEvidence, configuration.\n\n");
    sb.append("2. 'runbookEvidence':\n");
    sb.append("   - 'contractVersion': \"2.1\"\n");
    sb.append("   - 'schemaVersion': \"2.1\"\n");
    sb.append("   - 'facts': array of facts with 'factId', 'category', 'statement', 'confidence' (\"HIGH\"|\"MEDIUM\"|\"LOW\"), and 'sourceEvidence' array containing { \"file\": \"relative/path\", \"lineStart\": 1, \"lineEnd\": 10 } citing files from the provided context.\n\n");
    sb.append("3. 'securityFindings':\n");
    sb.append("   - 'contractVersion': \"2.1\"\n");
    sb.append("   - 'schemaVersion': \"2.1\"\n");
    sb.append("   - 'findings': array of security findings (or empty array [] if none).\n\n");

    sb.append("============================================================\n");
    sb.append("GROUNDING & REPOSITORY-EVIDENCE RULES\n");
    sb.append("============================================================\n");
    sb.append("1. Base all facts, flows, tables, topics, configurations, and troubleshooting steps ONLY on the provided repository context.\n");
    sb.append("2. DO NOT invent fictitious endpoints, database tables, Kafka topics, queues, timeouts, error codes, owners, or support contacts.\n");
    sb.append("3. If evidence for any section or concept is not found in the repository, use empty arrays [] or omit the field per canonical template.\n");
    sb.append("4. If a configuration property key is known from code/yaml but its runtime value is externalized in Config Server/Vault, set value to 'Check Config Portal' (or 'Protected - check Config Portal' for sensitive keys).\n");
    sb.append("5. DO NOT output plaintext secret values or passwords.\n\n");

    sb.append("============================================================\n");
    sb.append("PRODUCTION SUPPORT SAFETY RULES (CRITICAL)\n");
    sb.append("============================================================\n");
    sb.append("All Support troubleshooting actions, resolution steps, and verification instructions must be strictly READ-ONLY.\n");
    sb.append("You must NEVER instruct Support or Operations to:\n");
    sb.append("- Manually mutate, insert, update, or delete production database or Aerospike records.\n");
    sb.append("- Replay Kafka events or republish messages manually.\n");
    sb.append("- Alter, reset, rewind, advance, or seek Kafka consumer offsets.\n");
    sb.append("- Manually reprocess payments, transactions, or requests.\n");
    sb.append("- Manually force, trigger, or run schedulers, cron jobs, or background workers.\n");
    sb.append("- Modify production configuration files, properties, or secrets.\n");
    sb.append("- Perform manual state-changing reconciliation.\n\n");

    sb.append("============================================================\n");
    sb.append("CANONICAL TEMPLATES\n");
    sb.append("============================================================\n");
    sb.append("Template for 'runbookData':\n");
    sb.append(promptBuilder.runbookDataTemplate()).append("\n\n");
    sb.append("Template for 'runbookEvidence':\n");
    sb.append(promptBuilder.runbookEvidenceTemplate()).append("\n\n");
    sb.append("Template for 'securityFindings':\n");
    sb.append(promptBuilder.securityFindingsTemplate()).append("\n\n");

    sb.append("============================================================\n");
    sb.append("REPOSITORY SOURCE CODE CONTEXT\n");
    sb.append("============================================================\n");
    sb.append(repoContext != null ? repoContext : "(No context files collected)").append("\n");

    return sb.toString();
  }
}
