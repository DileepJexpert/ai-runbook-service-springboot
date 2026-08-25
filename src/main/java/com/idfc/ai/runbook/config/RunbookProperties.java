package com.idfc.ai.runbook.config;

import java.time.Duration;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("runbook")
public class RunbookProperties {
  private boolean enabled = true;
  private String artifactsRoot = "./build/runbook-artifacts";
  private String specRoot = null;

  private Generation generation = new Generation();
  private Ai ai = new Ai();
  private Collection collection = new Collection();
  private Agent agent = new Agent();
  private LocalInput localInput = new LocalInput();
  private Executor executor = new Executor();
  private Validation validation = new Validation();
  private Confluence confluence = new Confluence();
  private Map<String, Target> targets = new HashMap<>();

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean v) { enabled = v; }
  public String getArtifactsRoot() { return artifactsRoot; }
  public void setArtifactsRoot(String v) { artifactsRoot = v; }
  public String getSpecRoot() { return specRoot; }
  public void setSpecRoot(String v) { specRoot = v; }

  public Generation getGeneration() { return generation; }
  public void setGeneration(Generation v) { generation = v; }
  public Ai getAi() { return ai; }
  public void setAi(Ai v) { ai = v; }
  public Collection getCollection() { return collection; }
  public void setCollection(Collection v) { collection = v; }

  public Agent getAgent() { return agent; }
  public void setAgent(Agent v) { agent = v; }
  public LocalInput getLocalInput() { return localInput; }
  public void setLocalInput(LocalInput v) { localInput = v; }
  public Executor getExecutor() { return executor; }
  public void setExecutor(Executor v) { executor = v; }
  public Validation getValidation() { return validation; }
  public void setValidation(Validation v) { validation = v; }
  public Confluence getConfluence() { return confluence; }
  public void setConfluence(Confluence v) { confluence = v; }
  public Map<String, Target> getTargets() { return targets; }
  public void setTargets(Map<String, Target> v) { targets = v; }

  public static class Generation {
    private String mode = "DIRECT_STRUCTURED";
    public String getMode() { return mode; }
    public void setMode(String v) { mode = v; }
    public boolean isDirectStructured() { return "DIRECT_STRUCTURED".equalsIgnoreCase(mode); }
    public boolean isLean() { return "LEAN".equalsIgnoreCase(mode); }
    public boolean isStructured() { return "STRUCTURED".equalsIgnoreCase(mode); }
  }

  public static class Ai {
    private String baseUrl = "";
    private String authUrl = "";
    private String model = "gpt-4o";
    private String username = "";
    private String password = "";
    private int connectTimeoutSeconds = 300;
    private int requestTimeoutSeconds = 900;
    private int maxTokens = 12000;
    private double temperature = 0.0;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { baseUrl = v; }
    public String getAuthUrl() { return authUrl; }
    public void setAuthUrl(String v) { authUrl = v; }
    public String getModel() { return model; }
    public void setModel(String v) { model = v; }
    public String getModelName() { return model; }
    public void setModelName(String v) { model = v; }
    public String getUsername() { return username; }
    public void setUsername(String v) { username = v; }
    public String getPassword() { return password; }
    public void setPassword(String v) { password = v; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int v) { connectTimeoutSeconds = v; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int v) { requestTimeoutSeconds = v; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int v) { maxTokens = v; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double v) { temperature = v; }
  }

  public static class Collection {
    private int maxFiles = 120;
    private int maxTotalCharacters = 300000;
    private int maxFileCharacters = 20000;

    public int getMaxFiles() { return maxFiles; }
    public void setMaxFiles(int v) { maxFiles = v; }
    public int getMaxTotalCharacters() { return maxTotalCharacters; }
    public void setMaxTotalCharacters(int v) { maxTotalCharacters = v; }
    public int getMaxFileCharacters() { return maxFileCharacters; }
    public void setMaxFileCharacters(int v) { maxFileCharacters = v; }
  }

  public static class Agent {
    private String type = "local", executable = "idfc-coder";
    private Duration timeout = Duration.ofMinutes(30);
    private int maxCapturedLogBytes = 1048576;
    public String getType() { return type; }
    public void setType(String v) { type = v; }
    public String getExecutable() { return executable; }
    public void setExecutable(String v) { executable = v; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration v) { timeout = v; }
    public int getMaxCapturedLogBytes() { return maxCapturedLogBytes; }
    public void setMaxCapturedLogBytes(int v) { maxCapturedLogBytes = v; }
  }

  /** LOCAL/TEST-only allow-list for job supplied pre-generated extraction inputs. */
  public static class LocalInput {
    private Set<String> allowedRoots = new LinkedHashSet<>();
    public Set<String> getAllowedRoots() { return allowedRoots; }
    public void setAllowedRoots(Set<String> v) { allowedRoots = v == null ? new LinkedHashSet<>() : new LinkedHashSet<>(v); }
  }

  public static class Executor {
    private int corePoolSize = 2, maxPoolSize = 4, queueCapacity = 20;
    public int getCorePoolSize() { return corePoolSize; }
    public void setCorePoolSize(int v) { corePoolSize = v; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int v) { maxPoolSize = v; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int v) { queueCapacity = v; }
  }

  public static class Validation {
    private boolean requireCompleteScanForProduction = true;
    private int minimumRenderedBytes = 2000;
    public boolean isRequireCompleteScanForProduction() { return requireCompleteScanForProduction; }
    public void setRequireCompleteScanForProduction(boolean v) { requireCompleteScanForProduction = v; }
    public int getMinimumRenderedBytes() { return minimumRenderedBytes; }
    public void setMinimumRenderedBytes(int v) { minimumRenderedBytes = v; }
  }

  public static class Confluence {
    private boolean enabled;
    private Set<String> protectedPageIds = new HashSet<>();
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public Set<String> getProtectedPageIds() { return protectedPageIds; }
    public void setProtectedPageIds(Set<String> v) { protectedPageIds = v; }
  }

  public static class Target {
    private String spaceKey, testPageId, productionPageId;
    public String getSpaceKey() { return spaceKey; }
    public void setSpaceKey(String v) { spaceKey = v; }
    public String getTestPageId() { return testPageId; }
    public void setTestPageId(String v) { testPageId = v; }
    public String getProductionPageId() { return productionPageId; }
    public void setProductionPageId(String v) { productionPageId = v; }
  }
}
