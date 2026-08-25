package com.idfc.ai.runbook.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfc.ai.runbook.config.RunbookProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class RunbookAiAuthClient {
  private static final Logger log = LoggerFactory.getLogger(RunbookAiAuthClient.class);

  private final RunbookProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient httpClient;

  private String cachedToken;
  private Instant expiresAt = Instant.MIN;

  public RunbookAiAuthClient(RunbookProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(Math.max(5, properties.getAi().getConnectTimeoutSeconds())))
        .build();
  }

  public synchronized String getAccessToken() {
    if (cachedToken != null && Instant.now().plusSeconds(60).isBefore(expiresAt)) {
      return cachedToken;
    }
    return refreshToken();
  }

  public synchronized String refreshToken() {
    validateAuthConfig();
    String authUrl = properties.getAi().getAuthUrl();
    String username = properties.getAi().getUsername();
    String password = properties.getAi().getPassword();

    log.info("AI authentication started at auth endpoint");

    Map<String, String> payload = Map.of(
        "username", username,
        "password", password
    );

    String requestBody;
    try {
      requestBody = mapper.writeValueAsString(payload);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize authentication payload", e);
    }

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(authUrl))
        .timeout(Duration.ofSeconds(Math.max(10, properties.getAi().getRequestTimeoutSeconds())))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();

      if (status >= 200 && status < 300) {
        JsonNode root = mapper.readTree(response.body());
        String token = null;
        if (root.hasNonNull("access_token")) {
          token = root.get("access_token").asText();
        } else if (root.hasNonNull("token")) {
          token = root.get("token").asText();
        } else if (root.hasNonNull("accessToken")) {
          token = root.get("accessToken").asText();
        }

        if (token == null || token.isBlank()) {
          throw new IllegalStateException("RUNBOOK_AI_AUTH_FAILED: Authentication response did not contain access_token");
        }

        long expiresIn = 1800; // default 30 minutes
        if (root.hasNonNull("expires_in")) {
          expiresIn = root.get("expires_in").asLong(1800);
        } else if (root.hasNonNull("expiresIn")) {
          expiresIn = root.get("expiresIn").asLong(1800);
        }

        this.cachedToken = token;
        this.expiresAt = Instant.now().plusSeconds(expiresIn);
        log.info("AI authentication succeeded (token valid for {} seconds)", expiresIn);
        return token;
      }

      log.warn("AI authentication failed with HTTP status {}", status);
      throw new IllegalStateException("RUNBOOK_AI_AUTH_FAILED: Authentication endpoint returned HTTP " + status);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("AI authentication interrupted", e);
    } catch (IOException e) {
      throw new IllegalStateException("RUNBOOK_AI_AUTH_FAILED: Failed to connect to auth endpoint: " + e.getMessage(), e);
    }
  }

  public void validateAuthConfig() {
    String authUrl = properties.getAi().getAuthUrl();
    if (authUrl == null || authUrl.isBlank()) {
      throw new IllegalStateException("RUNBOOK_AI_AUTH_URL is not configured");
    }
    String username = properties.getAi().getUsername();
    if (username == null || username.isBlank()) {
      throw new IllegalStateException("RUNBOOK_AI_USERNAME is not configured");
    }
    String password = properties.getAi().getPassword();
    if (password == null || password.isBlank()) {
      throw new IllegalStateException("RUNBOOK_AI_PASSWORD is not configured");
    }
  }

  public synchronized void clearCache() {
    this.cachedToken = null;
    this.expiresAt = Instant.MIN;
  }

  public synchronized boolean isTokenCached() {
    return cachedToken != null && Instant.now().plusSeconds(60).isBefore(expiresAt);
  }
}
