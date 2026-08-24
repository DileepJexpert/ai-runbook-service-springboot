package com.idfc.ai.runbook.agent;

import com.idfc.ai.runbook.config.RunbookProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalIdfcCoderExecutorTest {

  @Test
  void commandLine_contains_executable_and_prompt_flag_with_single_prompt_argument() {
    RunbookProperties properties = new RunbookProperties();
    properties.getAgent().setExecutable("idfc-coder");
    LocalIdfcCoderExecutor executor = new LocalIdfcCoderExecutor(properties);

    String complexPrompt = "Read the centralized prompt\nLine 2 with \"quotes\" and spaces.\n{\n  \"key\": \"value\"\n}";
    List<String> commandLine = executor.buildCommandLine(complexPrompt);

    // 1. command contains idfc-coder
    assertThat(commandLine.get(0)).isEqualTo("idfc-coder");

    // 2. command contains -p or --prompt
    assertThat(commandLine.get(1)).isEqualTo("-p");

    // 3. assembled prompt is exactly one command argument (total command line size is exactly 3)
    assertThat(commandLine).hasSize(3);

    // 4. prompt containing spaces/newlines remains one argument
    assertThat(commandLine.get(2)).isEqualTo(complexPrompt);

    // 5. bare prompt invocation is not used (size is 3, not 2)
    assertThat(commandLine.size()).isNotEqualTo(2);
    assertThat(commandLine.get(1)).isNotEqualTo(complexPrompt);
  }

  @Test
  void processBuilder_inherits_commandLine_arguments_without_splitting_whitespace() {
    RunbookProperties properties = new RunbookProperties();
    properties.getAgent().setExecutable("idfc-coder");
    LocalIdfcCoderExecutor executor = new LocalIdfcCoderExecutor(properties);

    IdfcCoderRequest request = new IdfcCoderRequest(
        Path.of("."),
        Path.of("target/test-output"),
        "Multi-line\nPrompt\nWith Spaces",
        "Context With Spaces and Newlines\nLine 2",
        null, null, null,
        "BITBUCKET",
        "https://bitbucket.bank.local/scm/test/repo.git",
        "main",
        "abcdef123456"
    );

    String instruction = executor.buildInstruction(request);
    ProcessBuilder pb = executor.createProcessBuilder(request, instruction);

    List<String> command = pb.command();
    assertThat(command).hasSize(3);
    assertThat(command.get(0)).isEqualTo("idfc-coder");
    assertThat(command.get(1)).isEqualTo("-p");
    assertThat(command.get(2)).isEqualTo(instruction);
    assertThat(command.get(2)).contains("https://bitbucket.bank.local/scm/test/repo.git", "abcdef123456", "Multi-line\nPrompt\nWith Spaces");
  }

  @Test
  void failureMessage_sanitization_does_not_expose_full_extraction_prompt() {
    RunbookProperties properties = new RunbookProperties();
    LocalIdfcCoderExecutor executor = new LocalIdfcCoderExecutor(properties);

    String verboseError = "error: unrecognized arguments: Read the centralized prompt and context supplied below. Analyze the remote Bitbucket repository at ssh://git@bitbucket.devops.idfcbank.com:7999/jm/payments.git at commit 6ed4594439c50e6943e5dff52fc53ac41dbc68c5. PROMPT: Comprehensive contract details with full schema rules and regulatory evidence instructions...";

    String sanitized = executor.sanitizeError(verboseError, 2);

    assertThat(sanitized).startsWith("process exited with code 2: ");
    assertThat(sanitized).hasSizeLessThanOrEqualTo(250);
    assertThat(sanitized).doesNotContain("Comprehensive contract details with full schema rules and regulatory evidence instructions");
  }
}
