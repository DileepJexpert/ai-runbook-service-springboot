package com.idfc.ai.runbook.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfc.ai.runbook.config.RunbookProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnMissingBean(type = "com.idfc.ai.runbook.client.RunbookAiClient")
public class HttpRunbookAiClient implements RunbookAiClient {
  private static final Logger log = LoggerFactory.getLogger(HttpRunbookAiClient.class);

  private final RunbookProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient httpClient;
  private final RunbookAiAuthClient authClient;

  public HttpRunbookAiClient(RunbookProperties properties, ObjectMapper mapper, RunbookAiAuthClient authClient) {
    this.properties = properties;
    this.mapper = mapper;
    this.authClient = authClient;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(Math.max(5, properties.getAi().getConnectTimeoutSeconds())))
        .build();
  }

  @Override
  public String generate(String prompt) {
    validateAiConfig();

    String baseUrl = properties.getAi().getBaseUrl();
    String model = properties.getAi().getModel();
    int maxTokens = properties.getAi().getMaxTokens();
    double temperature = properties.getAi().getTemperature();

    String token = authClient.getAccessToken();

    Map<String, Object> requestPayload = Map.of(
        "model", model,
        "messages", List.of(Map.of("role", "user", "content", prompt)),
        "temperature", temperature,
        "max_tokens", maxTokens
    );

    String requestBody;
    try {
      requestBody = mapper.writeValueAsString(requestPayload);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to serialize AI request payload", e);
    }

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl))
        .timeout(Duration.ofSeconds(Math.max(10, properties.getAi().getRequestTimeoutSeconds())))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + token)
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build();

    int maxAttempts = 3;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        log.info("AI request started at {} (attempt {}/{})", baseUrl, attempt, maxAttempts);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
          log.info("AI request completed successfully");
          return extractContent(response.body());
        }

        log.warn("LLM API returned HTTP status {} on attempt {}", status, attempt);

        if ((status == 502 || status == 503 || status == 504) && attempt < maxAttempts) {
          Thread.sleep(1000L * attempt);
          continue;
        }

        if (status == 401 || status == 403) {
          authClient.clearCache();
          throw new IllegalStateException("RUNBOOK_AI_AUTH_FAILED: LLM API authentication failed with HTTP " + status);
        }

        throw new IllegalStateException("RUNBOOK_AGENT_FAILED: LLM API returned HTTP " + status + ": " + response.body());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("AI request interrupted", e);
      } catch (IOException e) {
        log.warn("I/O error calling LLM API on attempt {}: {}", attempt, e.getMessage());
        if (attempt < maxAttempts) {
          try {
            Thread.sleep(1000L * attempt);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI request interrupted", ie);
          }
          continue;
        }
        throw new IllegalStateException("RUNBOOK_AGENT_FAILED: Failed to communicate with LLM API: " + e.getMessage(), e);
      }
    }

    throw new IllegalStateException("RUNBOOK_AGENT_FAILED: AI request failed after " + maxAttempts + " attempts");
  }

  public void validateAiConfig() {
    String baseUrl = properties.getAi().getBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("RUNBOOK_AI_BASE_URL is not configured");
    }
    String model = properties.getAi().getModel();
    if (model == null || model.isBlank()) {
      throw new IllegalStateException("RUNBOOK_AI_MODEL is not configured");
    }
    authClient.validateAuthConfig();
  }

  private String extractContent(String responseJson) {
    try {
      JsonNode root = mapper.readTree(responseJson);
      // Standard OpenAI / Azure Chat completion format
      JsonNode choices = root.path("choices");
      if (choices.isArray() && !choices.isEmpty()) {
        JsonNode message = choices.get(0).path("message");
        if (message.hasNonNull("content")) {
          return stripMarkdownWrapper(message.path("content").asText());
        }
        if (choices.get(0).hasNonNull("text")) {
          return stripMarkdownWrapper(choices.get(0).path("text").asText());
        }
      }
      // Alternate direct response format
      if (root.hasNonNull("response")) {
        return stripMarkdownWrapper(root.path("response").asText());
      }
      if (root.hasNonNull("content")) {
        return stripMarkdownWrapper(root.path("content").asText());
      }
      return stripMarkdownWrapper(responseJson);
    } catch (Exception e) {
      log.debug("Response is not JSON, returning raw text: {}", e.getMessage());
      return stripMarkdownWrapper(responseJson);
    }
  }

  private String stripMarkdownWrapper(String text) {
    if (text == null) return "";
    String trimmed = text.trim();
    if (trimmed.startsWith("```markdown") && trimmed.endsWith("```")) {
      return trimmed.substring("```markdown".length(), trimmed.length() - 3).trim();
    }
    if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
      return trimmed.substring(3, trimmed.length() - 3).trim();
    }
    return trimmed;
  }
}
