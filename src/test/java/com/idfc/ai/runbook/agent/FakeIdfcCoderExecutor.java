package com.idfc.ai.runbook.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@TestConfiguration
public class FakeIdfcCoderExecutor {
  @Bean
  public IdfcCoderExecutor fakeAgent() {
    ObjectMapper mapper = new ObjectMapper();
    return request -> {
      try {
        Files.createDirectories(request.outputDirectory());
        String dataJson = Files.readString(Path.of("src/test/resources/fixtures/runbook-data.json"));
        if (request.commitSha() != null && !request.commitSha().isBlank()) {
          ObjectNode root = (ObjectNode) mapper.readTree(dataJson);
          ((ObjectNode) root.path("pipeline")).put("gitCommitSha", request.commitSha());
          Files.writeString(request.outputDirectory().resolve("runbook-data.json"), mapper.writeValueAsString(root));
        } else {
          Files.copy(Path.of("src/test/resources/fixtures/runbook-data.json"), request.outputDirectory().resolve("runbook-data.json"), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(Path.of("src/test/resources/fixtures/runbook-evidence.json"), request.outputDirectory().resolve("runbook-evidence.json"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(request.outputDirectory().resolve("security-findings.json"), "{\"contractVersion\":\"2.1\",\"findings\":[]}\n");
        return new IdfcCoderResult("fake extraction", "");
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    };
  }
}
