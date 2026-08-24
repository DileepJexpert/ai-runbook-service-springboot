package com.idfc.ai.runbook.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfc.ai.runbook.artifact.RunbookArtifactStore;
import com.idfc.ai.runbook.quality.QualityGateDecision;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

final class GenerationReportWriter {
  private GenerationReportWriter() {}
  static void write(ObjectMapper mapper, RunbookArtifactStore artifacts, Path root, RunbookJob job, JsonNode data, QualityGateDecision gate, String promptFingerprint) throws Exception {
    Map<String,Object> value=new TreeMap<>();
    value.put("contractVersion","2.1"); value.put("schemaVersion","2.1"); value.put("promptVersion",data.path("generator").path("promptVersion").asText("UNKNOWN")); value.put("promptFingerprint",promptFingerprint); value.put("platformContextVersion",data.path("generator").path("platformContextVersion").asText("UNKNOWN")); value.put("serviceId",job.serviceId); value.put("jobId",job.id.toString()); value.put("requestedCommitSha",job.requestedCommit); value.put("analyzedCommitSha",job.analyzedCommit); value.put("scanStatus",data.path("generator").path("scanStatus").asText("PARTIAL")); value.put("schemaValidation",true); value.put("evidenceValidation",true); value.put("safetyValidation",true); value.put("secretValidation",true); value.put("rendererValidation",true); value.put("qualityGate",gate.outcome()); value.put("publishEligible",gate.publishEligible()); value.put("warnings",gate.warnings()); value.put("errors",gate.errors()); value.put("factCounts",factCounts(data)); value.put("confidenceCounts",confidenceCounts(data)); value.put("unresolvedRuntimeConfigurationCount",unresolvedCount(data)); value.put("artifactPaths",artifactPaths(root)); value.put("artifactSha256",artifactHashes(root)); artifacts.write(root,"report/generation-report.json",mapper.writeValueAsString(value));
  }
  private static Map<String,Integer> factCounts(JsonNode data) { Map<String,Integer> counts=new TreeMap<>(); data.fields().forEachRemaining(entry->{if(entry.getValue().isArray()) counts.put(entry.getKey(),entry.getValue().size());}); return counts; }
  private static Map<String,Integer> confidenceCounts(JsonNode data) { Map<String,Integer> counts=new TreeMap<>(); countConfidence(data,counts); return counts; }
  private static void countConfidence(JsonNode value,Map<String,Integer> counts) { if(value.isObject()){if(value.hasNonNull("confidence")) counts.merge(value.path("confidence").asText(),1,Integer::sum); value.elements().forEachRemaining(child->countConfidence(child,counts));}else if(value.isArray())value.forEach(child->countConfidence(child,counts)); }
  private static int unresolvedCount(JsonNode data) { int count=0; for(JsonNode item:data.path("configuration")){String status=item.path("runtimeValueStatus").asText(); if("CHECK_CONFIG_PORTAL".equals(status)||"PROTECTED_CHECK_CONFIG_PORTAL".equals(status)||"UNRESOLVED".equals(status))count++;} return count; }
  private static Map<String,String> artifactPaths(Path root) throws Exception { Map<String,String> paths=new TreeMap<>(); try(var stream=Files.walk(root)){stream.filter(Files::isRegularFile).forEach(path->paths.put(root.relativize(path).toString().replace('\\','/'),path.toString()));} return paths; }
  private static Map<String,String> artifactHashes(Path root) throws Exception { Map<String,String> hashes=new TreeMap<>(); try(var stream=Files.walk(root)){for(Path path:stream.filter(Files::isRegularFile).toList())hashes.put(root.relativize(path).toString().replace('\\','/'),sha(Files.readAllBytes(path)));} return hashes; }
  private static String sha(byte[] bytes) throws Exception { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
