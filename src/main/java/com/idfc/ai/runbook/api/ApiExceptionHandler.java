package com.idfc.ai.runbook.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
    String message = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : "RUNBOOK_JOB_NOT_FOUND";
    String code = message.startsWith("RUNBOOK_") ? message.split("[: ]")[0] : "RUNBOOK_JOB_NOT_FOUND";
    log.warn("Resource not found: {}", message);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
        "code", code,
        "message", message
    ));
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  public ResponseEntity<Map<String, String>> handleBadRequest(Exception e) {
    String message = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : "Runbook request could not be completed";
    String code = message.startsWith("RUNBOOK_") || message.startsWith("SAFETY_") ? message.split("[: ]")[0] : "RUNBOOK_REQUEST_INVALID";
    log.warn("Bad request: code={} message={}", code, message);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
        "code", code,
        "message", message
    ));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
    log.error("Unhandled API exception: {}", e.getMessage(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
        "code", "RUNBOOK_INTERNAL_ERROR",
        "message", e.getMessage() != null ? e.getMessage() : "An unexpected internal error occurred"
    ));
  }
}
