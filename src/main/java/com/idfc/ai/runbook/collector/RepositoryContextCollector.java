package com.idfc.ai.runbook.collector;

import com.idfc.ai.runbook.config.RunbookProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RepositoryContextCollector {
  private static final Logger log = LoggerFactory.getLogger(RepositoryContextCollector.class);

  private static final Set<String> EXCLUDED_DIR_NAMES = Set.of(
      ".git", ".idea", ".vscode", "target", "build", "out", "node_modules",
      ".mvn", ".gradle", ".settings", "bin", "dist", "coverage", ".system_generated"
  );

  private static final Set<String> BINARY_EXTENSIONS = Set.of(
      ".jar", ".war", ".zip", ".tar", ".gz", ".png", ".jpg", ".jpeg", ".gif",
      ".pdf", ".class", ".exe", ".dll", ".so", ".dylib", ".woff", ".woff2",
      ".ttf", ".eot", ".ico", ".svg", ".pyc", ".7z"
  );

  private static final Pattern SECRET_CONFIG_LINE = Pattern.compile(
      "(?i)^(\\s*[\\w.\\-]*(?:password|passwd|pwd|secret|api[_-]?key|apikey|auth[_-]?token|private[_-]?key)[\\w.\\-]*\\s*[:=]\\s*)(.+)$"
  );

  private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("^\\s*\\$\\{[^}]+\\}\\s*$");

  private final RunbookProperties properties;

  public RepositoryContextCollector(RunbookProperties properties) {
    this.properties = properties;
  }

  public record ContextResult(
      String contextText,
      int filesIncluded,
      int totalCharacters,
      List<String> includedFiles
  ) {}

  public ContextResult collect(Path workspaceRoot) {
    if (workspaceRoot == null || !Files.isDirectory(workspaceRoot)) {
      return new ContextResult("", 0, 0, List.of());
    }

    int maxFiles = properties.getCollection().getMaxFiles();
    int maxTotalChars = properties.getCollection().getMaxTotalCharacters();
    int maxFileChars = properties.getCollection().getMaxFileCharacters();

    List<Path> candidateFiles = discoverCandidateFiles(workspaceRoot);
    candidateFiles.sort((p1, p2) -> Integer.compare(priorityOf(workspaceRoot.relativize(p1)), priorityOf(workspaceRoot.relativize(p2))));

    StringBuilder sb = new StringBuilder();
    List<String> included = new ArrayList<>();
    int filesCount = 0;

    for (Path file : candidateFiles) {
      if (filesCount >= maxFiles) {
        sb.append("\n[Context limit reached: maximum file limit ").append(maxFiles).append(" reached]\n");
        break;
      }

      String relativePath = workspaceRoot.relativize(file).toString().replace('\\', '/');
      String content;
      try {
        content = Files.readString(file);
      } catch (Exception e) {
        log.debug("Skipping unreadable file: {}", relativePath);
        continue;
      }

      content = sanitizeAndRedact(relativePath, content);

      if (content.length() > maxFileChars) {
        content = content.substring(0, maxFileChars) + "\n[... truncated ...]\n";
      }

      String fileEntry = "FILE: " + relativePath + "\n" + content + "\n\n";

      if (sb.length() + fileEntry.length() > maxTotalChars && sb.length() > 0) {
        sb.append("\n[Context limit reached: maximum total character limit ").append(maxTotalChars).append(" reached]\n");
        break;
      }

      sb.append(fileEntry);
      included.add(relativePath);
      filesCount++;
    }

    log.info("RepositoryContextCollector collected {} files ({} chars) from workspace {}",
        filesCount, sb.length(), workspaceRoot);

    return new ContextResult(sb.toString(), filesCount, sb.length(), included);
  }

  private List<Path> discoverCandidateFiles(Path root) {
    List<Path> files = new ArrayList<>();
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
          if (EXCLUDED_DIR_NAMES.contains(name)) {
            return FileVisitResult.SKIP_SUBTREE;
          }
          // Exclude src/test directories
          Path rel = root.relativize(dir);
          String relStr = rel.toString().replace('\\', '/');
          if (relStr.equals("src/test") || relStr.startsWith("src/test/")) {
            return FileVisitResult.SKIP_SUBTREE;
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
          String name = file.getFileName().toString();
          String lower = name.toLowerCase();

          for (String ext : BINARY_EXTENSIONS) {
            if (lower.endsWith(ext)) return FileVisitResult.CONTINUE;
          }

          Path rel = root.relativize(file);
          String relStr = rel.toString().replace('\\', '/');

          // Skip root hidden files (.gitignore, .gitattributes etc are okay, but .env, .DS_Store skip)
          if (name.equals(".DS_Store") || name.startsWith(".env")) {
            return FileVisitResult.CONTINUE;
          }

          files.add(file);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      log.warn("Error walking workspace directory: {}", root, e);
    }
    return files;
  }

  private int priorityOf(Path relativePath) {
    String path = relativePath.toString().replace('\\', '/').toLowerCase();
    if (path.startsWith("readme")) return 10;
    if (path.equals("pom.xml") || path.startsWith("build.gradle") || path.startsWith("settings.gradle")) return 20;
    if (path.contains("application") || path.contains("bootstrap")) return 30;
    if (path.contains("db/migration") || path.contains("liquibase") || path.contains("flyway")) return 40;
    if (path.contains("controller") || path.contains("resource") || path.contains("endpoint")) return 50;
    if (path.contains("service") || path.contains("handler") || path.contains("processor")) return 60;
    if (path.contains("consumer") || path.contains("producer") || path.contains("kafka") || path.contains("listener")) return 70;
    if (path.contains("client") || path.contains("feign") || path.contains("integration")) return 80;
    if (path.contains("exception") || path.contains("error") || path.contains("advice")) return 90;
    if (path.contains("repository") || path.contains("dao")) return 100;
    if (path.contains("model") || path.contains("entity") || path.contains("dto")) return 110;
    if (path.contains("config") || path.contains("security")) return 120;
    if (path.startsWith("docs/")) return 130;
    if (path.startsWith("src/main/")) return 140;
    return 200;
  }

  private String sanitizeAndRedact(String relativePath, String content) {
    String lower = relativePath.toLowerCase();
    if (lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties")) {
      String[] lines = content.split("\\R");
      StringBuilder sb = new StringBuilder();
      for (String line : lines) {
        Matcher matcher = SECRET_CONFIG_LINE.matcher(line);
        if (matcher.matches()) {
          String prefix = matcher.group(1);
          String value = matcher.group(2).trim();
          if (PLACEHOLDER_PATTERN.matcher(value).matches() || value.isBlank() || value.equals("[PROTECTED]")) {
            sb.append(line).append("\n");
          } else {
            sb.append(prefix).append("[PROTECTED]\n");
          }
        } else {
          sb.append(line).append("\n");
        }
      }
      return sb.toString();
    }
    return content;
  }
}
