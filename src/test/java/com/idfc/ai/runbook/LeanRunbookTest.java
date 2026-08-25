package com.idfc.ai.runbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfc.ai.runbook.agent.LeanRunbookPromptBuilder;
import com.idfc.ai.runbook.client.FakeRunbookAiClient;
import com.idfc.ai.runbook.client.HttpRunbookAiClient;
import com.idfc.ai.runbook.client.RunbookAiAuthClient;
import com.idfc.ai.runbook.collector.RepositoryContextCollector;
import com.idfc.ai.runbook.config.RunbookProperties;
import com.idfc.ai.runbook.rendering.LeanMarkdownToHtmlConverter;
import com.idfc.ai.runbook.validation.LeanRunbookValidator;
import com.idfc.ai.runbook.validation.RunbookSafetyValidator;
import com.idfc.ai.runbook.validation.ValidationResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeanRunbookTest {

  private final RunbookSafetyValidator safetyValidator = new RunbookSafetyValidator();
  private final LeanRunbookValidator leanValidator = new LeanRunbookValidator(safetyValidator);
  private final LeanMarkdownToHtmlConverter htmlConverter = new LeanMarkdownToHtmlConverter();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void collector_excludes_git_target_build_and_test_directories() throws Exception {
    Path tempRepo = Files.createTempDirectory("collector-test-repo");

    // Create excluded dirs
    Path gitDir = Files.createDirectories(tempRepo.resolve(".git"));
    Files.writeString(gitDir.resolve("config"), "git-config-content");

    Path targetDir = Files.createDirectories(tempRepo.resolve("target"));
    Files.writeString(targetDir.resolve("some.class"), "compiled-bytecode");

    Path buildDir = Files.createDirectories(tempRepo.resolve("build"));
    Files.writeString(buildDir.resolve("out.txt"), "build-output");

    Path testDir = Files.createDirectories(tempRepo.resolve("src/test/java"));
    Files.writeString(testDir.resolve("MyTest.java"), "class MyTest {}");

    // Create included files
    Files.writeString(tempRepo.resolve("README.md"), "# Payment Service\nDocumentation");
    Files.writeString(tempRepo.resolve("pom.xml"), "<project>pom</project>");

    Path mainSrc = Files.createDirectories(tempRepo.resolve("src/main/java/com/idfc"));
    Files.writeString(mainSrc.resolve("PaymentController.java"), "@RestController class PaymentController {}");

    Path mainRes = Files.createDirectories(tempRepo.resolve("src/main/resources"));
    Files.writeString(mainRes.resolve("application.yml"), "server:\n  port: 8080");

    RunbookProperties properties = new RunbookProperties();
    RepositoryContextCollector collector = new RepositoryContextCollector(properties);

    var result = collector.collect(tempRepo);

    assertThat(result.filesIncluded()).isGreaterThanOrEqualTo(4);
    assertThat(result.contextText()).contains("FILE: README.md", "FILE: pom.xml", "FILE: src/main/java/com/idfc/PaymentController.java", "FILE: src/main/resources/application.yml");
    assertThat(result.contextText()).doesNotContain("git-config-content", "some.class", "build-output", "MyTest.java");
  }

  @Test
  void collector_redacts_sensitive_configuration_secrets() throws Exception {
    Path tempRepo = Files.createTempDirectory("secret-redact-repo");
    Path mainRes = Files.createDirectories(tempRepo.resolve("src/main/resources"));

    Files.writeString(mainRes.resolve("application.yml"), """
        spring:
          datasource:
            url: jdbc:postgresql://localhost:5432/db
            username: myuser
            password: SuperSecretPassword123!
          kafka:
            api-key: secret-api-key-raw-value
            safe-token: ${SAFE_ENV_TOKEN}
        """);

    RunbookProperties properties = new RunbookProperties();
    RepositoryContextCollector collector = new RepositoryContextCollector(properties);

    var result = collector.collect(tempRepo);

    assertThat(result.contextText()).contains("username: myuser");
    assertThat(result.contextText()).contains("password: [PROTECTED]");
    assertThat(result.contextText()).contains("api-key: [PROTECTED]");
    assertThat(result.contextText()).contains("safe-token: ${SAFE_ENV_TOKEN}");
    assertThat(result.contextText()).doesNotContain("SuperSecretPassword123!");
    assertThat(result.contextText()).doesNotContain("secret-api-key-raw-value");
  }

  @Test
  void collector_enforces_max_files_and_character_limits() throws Exception {
    Path tempRepo = Files.createTempDirectory("limit-repo");

    for (int i = 0; i < 10; i++) {
      Files.writeString(tempRepo.resolve("file" + i + ".txt"), "content " + i);
    }

    RunbookProperties properties = new RunbookProperties();
    properties.getCollection().setMaxFiles(3);
    properties.getCollection().setMaxTotalCharacters(1000);

    RepositoryContextCollector collector = new RepositoryContextCollector(properties);
    var result = collector.collect(tempRepo);

    assertThat(result.filesIncluded()).isEqualTo(3);
    assertThat(result.contextText()).contains("[Context limit reached");
  }

  @Test
  void prompt_builder_contains_all_23_headings_and_safety_rules() {
    LeanRunbookPromptBuilder builder = new LeanRunbookPromptBuilder();
    String prompt = builder.buildPrompt(
        "payments-service",
        "BITBUCKET",
        "https://bitbucket.bank.local/scm/pay/payments.git",
        "6ed4594",
        "FILE: pom.xml\n<project/>"
    );

    assertThat(prompt).contains("payments-service", "6ed4594", "https://bitbucket.bank.local/scm/pay/payments.git");

    // All 23 headings
    for (String section : LeanRunbookPromptBuilder.FROZEN_SECTIONS) {
      assertThat(prompt).contains("## " + section);
    }

    // Evidence & Grounding rules
    assertThat(prompt).contains("Not found in repository");
    assertThat(prompt).contains("Check Config Portal");
    assertThat(prompt).contains("DO NOT invent fictitious endpoints");

    // Read-only Support safety rules
    assertThat(prompt).contains("READ-ONLY");
    assertThat(prompt).contains("Manually mutate, insert, update, or delete production database");
    assertThat(prompt).contains("Replay Kafka events");
    assertThat(prompt).contains("Alter, reset, rewind, advance, or seek Kafka consumer offsets");
    assertThat(prompt).contains("Perform manual state-changing reconciliation");
  }

  @Test
  void validator_passes_for_valid_23_section_markdown() {
    ValidationResult result = leanValidator.validate(FakeRunbookAiClient.VALID_SAMPLE_RUNBOOK_MD, "payments-service", "develop-head-sha-9999");
    assertThat(result.valid()).withFailMessage(String.join(", ", result.errors())).isTrue();
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void validator_fails_when_section_heading_is_missing() {
    String brokenMd = """
        # Production Support Runbook — payments-service

        ## 1. Service Overview & Criticality
        Payments service overview.

        ## 2. Quick Support Summary
        Summary.
        """;

    ValidationResult result = leanValidator.validate(brokenMd, "payments-service", "6ed4594");
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(e -> e.contains("RUNBOOK_MISSING_SECTION"));
  }

  @Test
  void validator_fails_when_unsafe_support_instruction_present() {
    String unsafeMd = FakeRunbookAiClient.VALID_SAMPLE_RUNBOOK_MD + "\n\nSupport should manually update database table records to fix status.";
    ValidationResult result = leanValidator.validate(unsafeMd, "payments-service", "develop-head-sha-9999");
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(e -> e.contains("NO_MANUAL_DATABASE_MUTATION") || e.contains("SAFETY_POLICY_VIOLATION"));
  }

  @Test
  void validator_fails_when_secret_value_detected() {
    String secretMd = FakeRunbookAiClient.VALID_SAMPLE_RUNBOOK_MD + "\n\nUse password = MyPlainPassword123 for connecting.";
    ValidationResult result = leanValidator.validate(secretMd, "payments-service", "develop-head-sha-9999");
    assertThat(result.valid()).isFalse();
    assertThat(result.errors()).anyMatch(e -> e.contains("NO_PASSWORD_ASSIGNMENT") || e.contains("SECRET_VALUE_DETECTED"));
  }

  @Test
  void html_converter_renders_headers_tables_and_lists() {
    String markdown = """
        # Title

        ## 1. Service Overview & Criticality
        Overview paragraph with **bold text** and `inline code`.

        | Header A | Header B |
        | --- | --- |
        | Value 1 | Value 2 |

        - List item 1
        - List item 2
        """;

    String html = htmlConverter.convertToHtml(markdown);

    assertThat(html).contains("<!-- AUTO-GENERATED START -->");
    assertThat(html).contains("<h1>Title</h1>");
    assertThat(html).contains("<h2>1. Service Overview &amp; Criticality</h2>");
    assertThat(html).contains("<p>Overview paragraph with <strong>bold text</strong> and <code>inline code</code>.</p>");
    assertThat(html).contains("<table>");
    assertThat(html).contains("<th>Header A</th>");
    assertThat(html).contains("<td>Value 1</td>");
    assertThat(html).contains("<ul>", "<li>List item 1</li>", "<li>List item 2</li>", "</ul>");
    assertThat(html).contains("<!-- AUTO-GENERATED END -->");
  }

  @Test
  void auth_client_validates_missing_configuration_clearly() {
    RunbookProperties props = new RunbookProperties();
    RunbookAiAuthClient authClient = new RunbookAiAuthClient(props, mapper);

    assertThatThrownBy(authClient::getAccessToken)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RUNBOOK_AI_AUTH_URL is not configured");

    props.getAi().setAuthUrl("http://localhost:8080/auth");
    assertThatThrownBy(authClient::getAccessToken)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RUNBOOK_AI_USERNAME is not configured");

    props.getAi().setUsername("test-user");
    assertThatThrownBy(authClient::getAccessToken)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RUNBOOK_AI_PASSWORD is not configured");
  }

  @Test
  void auth_client_fetches_caches_and_refreshes_token() throws Exception {
    AtomicInteger authRequests = new AtomicInteger(0);
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/auth", exchange -> {
      authRequests.incrementAndGet();
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      assertThat(body).contains("test-user", "test-secret-pass");

      String responseJson = "{\"access_token\": \"mock-jwt-token-12345\", \"expires_in\": 3600}";
      byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(bytes);
      }
    });
    server.start();

    try {
      int port = server.getAddress().getPort();
      RunbookProperties props = new RunbookProperties();
      props.getAi().setAuthUrl("http://localhost:" + port + "/auth");
      props.getAi().setUsername("test-user");
      props.getAi().setPassword("test-secret-pass");

      RunbookAiAuthClient authClient = new RunbookAiAuthClient(props, mapper);

      // First call fetches from endpoint
      String token1 = authClient.getAccessToken();
      assertThat(token1).isEqualTo("mock-jwt-token-12345");
      assertThat(authRequests.get()).isEqualTo(1);
      assertThat(authClient.isTokenCached()).isTrue();

      // Second call uses memory cache
      String token2 = authClient.getAccessToken();
      assertThat(token2).isEqualTo("mock-jwt-token-12345");
      assertThat(authRequests.get()).isEqualTo(1); // No new network call

      // Clear cache and call again
      authClient.clearCache();
      assertThat(authClient.isTokenCached()).isFalse();
      String token3 = authClient.getAccessToken();
      assertThat(token3).isEqualTo("mock-jwt-token-12345");
      assertThat(authRequests.get()).isEqualTo(2); // Network call triggered
    } finally {
      server.stop(0);
    }
  }

  @Test
  void auth_client_handles_authentication_failure_without_leaking_credentials() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/auth", exchange -> {
      String responseJson = "{\"error\": \"invalid_credentials\"}";
      byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(401, bytes.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(bytes);
      }
    });
    server.start();

    try {
      int port = server.getAddress().getPort();
      RunbookProperties props = new RunbookProperties();
      props.getAi().setAuthUrl("http://localhost:" + port + "/auth");
      props.getAi().setUsername("test-user");
      props.getAi().setPassword("super-secret-password-xyz");

      RunbookAiAuthClient authClient = new RunbookAiAuthClient(props, mapper);

      assertThatThrownBy(authClient::getAccessToken)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("RUNBOOK_AI_AUTH_FAILED")
          .hasMessageNotContaining("super-secret-password-xyz");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void http_ai_client_sends_authorization_bearer_and_extracts_markdown() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/auth", exchange -> {
      String responseJson = "{\"access_token\": \"my-valid-bearer-token\", \"expires_in\": 3600}";
      byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    });

    server.createContext("/chat", exchange -> {
      String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
      assertThat(authHeader).isEqualTo("Bearer my-valid-bearer-token");

      String responseJson = """
          {
            "choices": [
              {
                "message": {
                  "role": "assistant",
                  "content": "# Production Support Runbook — payments-service\\n\\n## 1. Service Overview & Criticality\\nOverview"
                }
              }
            ]
          }
          """;
      byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    });
    server.start();

    try {
      int port = server.getAddress().getPort();
      RunbookProperties props = new RunbookProperties();
      props.getAi().setBaseUrl("http://localhost:" + port + "/chat");
      props.getAi().setAuthUrl("http://localhost:" + port + "/auth");
      props.getAi().setUsername("test-user");
      props.getAi().setPassword("test-pass");
      props.getAi().setModel("gpt-4o");

      RunbookAiAuthClient authClient = new RunbookAiAuthClient(props, mapper);
      HttpRunbookAiClient client = new HttpRunbookAiClient(props, mapper, authClient);

      String result = client.generate("Generate runbook for payments-service");
      assertThat(result).contains("# Production Support Runbook — payments-service", "## 1. Service Overview & Criticality");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void http_ai_client_retries_transient_502_503_504_errors() throws Exception {
    AtomicInteger attempts = new AtomicInteger(0);
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/auth", exchange -> {
      String responseJson = "{\"access_token\": \"token-retry\", \"expires_in\": 3600}";
      byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    });

    server.createContext("/chat", exchange -> {
      int cur = attempts.incrementAndGet();
      if (cur < 3) {
        exchange.sendResponseHeaders(503, 0);
        exchange.close();
      } else {
        String responseJson = "{\"choices\":[{\"message\":{\"content\":\"# Runbook Success\"}}]}";
        byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
      }
    });
    server.start();

    try {
      int port = server.getAddress().getPort();
      RunbookProperties props = new RunbookProperties();
      props.getAi().setBaseUrl("http://localhost:" + port + "/chat");
      props.getAi().setAuthUrl("http://localhost:" + port + "/auth");
      props.getAi().setUsername("test-user");
      props.getAi().setPassword("test-pass");
      props.getAi().setModel("gpt-4o");

      RunbookAiAuthClient authClient = new RunbookAiAuthClient(props, mapper);
      HttpRunbookAiClient client = new HttpRunbookAiClient(props, mapper, authClient);

      String result = client.generate("prompt");
      assertThat(result).isEqualTo("# Runbook Success");
      assertThat(attempts.get()).isEqualTo(3);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void http_ai_client_fails_on_401_without_blind_retries() throws Exception {
    AtomicInteger attempts = new AtomicInteger(0);
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/auth", exchange -> {
      String responseJson = "{\"access_token\": \"token-bad\", \"expires_in\": 3600}";
      byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    });

    server.createContext("/chat", exchange -> {
      attempts.incrementAndGet();
      exchange.sendResponseHeaders(401, 0);
      exchange.close();
    });
    server.start();

    try {
      int port = server.getAddress().getPort();
      RunbookProperties props = new RunbookProperties();
      props.getAi().setBaseUrl("http://localhost:" + port + "/chat");
      props.getAi().setAuthUrl("http://localhost:" + port + "/auth");
      props.getAi().setUsername("test-user");
      props.getAi().setPassword("test-pass");
      props.getAi().setModel("gpt-4o");

      RunbookAiAuthClient authClient = new RunbookAiAuthClient(props, mapper);
      HttpRunbookAiClient client = new HttpRunbookAiClient(props, mapper, authClient);

      assertThatThrownBy(() -> client.generate("prompt"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("RUNBOOK_AI_AUTH_FAILED");

      // Verified: 401 was NOT retried blindly
      assertThat(attempts.get()).isEqualTo(1);
    } finally {
      server.stop(0);
    }
  }
}
