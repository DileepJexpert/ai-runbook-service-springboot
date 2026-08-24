package com.idfc.ai.runbook.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.idfc.ai.runbook.config.RunbookProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileBasedIdfcCoderExecutorTest {
  @TempDir Path temp;

  @Test
  void copies_each_job_input_into_its_own_extraction_directory() throws Exception {
    Path inputRoot = Files.createDirectory(temp.resolve("inputs"));
    Path first = Files.createDirectory(inputRoot.resolve("first"));
    Path second = Files.createDirectory(inputRoot.resolve("second"));
    Path firstData = write(first, "runbook-data.json", "{\"service\":\"first\"}");
    Path firstEvidence = write(first, "runbook-evidence.json", "{\"evidence\":\"first\"}");
    Path secondData = write(second, "runbook-data.json", "{\"service\":\"second\"}");
    Path secondEvidence = write(second, "runbook-evidence.json", "{\"evidence\":\"second\"}");
    RunbookProperties properties = properties(inputRoot);
    FileBasedIdfcCoderExecutor executor = new FileBasedIdfcCoderExecutor(properties);

    Path firstOutput = temp.resolve("artifacts/first-job/extraction");
    Path secondOutput = temp.resolve("artifacts/second-job/extraction");
    executor.execute(new IdfcCoderRequest(temp, firstOutput, "prompt", "context", firstData, firstEvidence));
    executor.execute(new IdfcCoderRequest(temp, secondOutput, "prompt", "context", secondData, secondEvidence));

    assertThat(Files.readString(firstOutput.resolve("runbook-data.json"))).contains("first");
    assertThat(Files.readString(secondOutput.resolve("runbook-data.json"))).contains("second");
    assertThat(Files.readString(firstOutput.resolve("runbook-data.json"))).doesNotContain("second");
  }

  @Test
  void rejects_a_job_input_outside_the_local_allow_list() throws Exception {
    Path allowed = Files.createDirectory(temp.resolve("allowed"));
    Path forbidden = Files.createDirectory(temp.resolve("forbidden"));
    Path data = write(forbidden, "runbook-data.json", "{}");
    Path evidence = write(forbidden, "runbook-evidence.json", "{}");
    FileBasedIdfcCoderExecutor executor = new FileBasedIdfcCoderExecutor(properties(allowed));

    assertThatThrownBy(() -> executor.execute(new IdfcCoderRequest(temp, temp.resolve("out"), "prompt", "context", data, evidence)))
        .hasMessageContaining("outside local-input allowed roots");
  }

  @Test
  void copies_optional_security_findings_only_into_the_job_extraction_directory() throws Exception {
    Path input = Files.createDirectory(temp.resolve("security-input"));
    Path data = write(input, "runbook-data.json", "{}");
    Path evidence = write(input, "runbook-evidence.json", "{}");
    Path security = write(input, "security-findings.json", "{\"findings\":[]}");
    Path output = temp.resolve("artifacts/security-job/extraction");
    new FileBasedIdfcCoderExecutor(properties(input)).execute(new IdfcCoderRequest(temp, output, "prompt", "context", data, evidence, security));

    assertThat(Files.readString(output.resolve("security-findings.json"))).contains("findings");
  }

  private RunbookProperties properties(Path inputRoot) {
    RunbookProperties properties = new RunbookProperties();
    properties.getLocalInput().setAllowedRoots(Set.of(inputRoot.toString()));
    return properties;
  }

  private Path write(Path directory, String name, String content) throws Exception {
    Path file = directory.resolve(name);
    Files.writeString(file, content);
    return file;
  }
}
