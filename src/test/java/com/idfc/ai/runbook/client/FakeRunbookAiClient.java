package com.idfc.ai.runbook.client;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@TestConfiguration
public class FakeRunbookAiClient {

  public static final String VALID_SAMPLE_RUNBOOK_MD = """
      # Production Support Runbook — payments-service

      ## 1. Service Overview & Criticality
      Payments Processing Service for payments-service. High criticality. Business Owner: Payments. Support Owner: Platform Support. Escalation Channel: #payments-support.

      ## 2. Quick Support Summary
      Handles credit and debit card transactions and IMPS payments.

      ## 3. Business Flow & Decision Guide
      Incoming API calls validate payload, check account limits, and forward to backend CBS.

      ## 4. API / Event Contract & Validation
      | Endpoint/Event | Method/Direction | Authentication / Scope | Processing Model | Validation Summary | Success Meaning |
      | /v1/payments | POST | Bearer JWT | SYNCHRONOUS | Validates amount, currency, account | Payment accepted |

      ## 5. Business Decision Rules
      | Business Condition | Data Used | Result / Response | Support Check |
      | Amount > 500000 | txn.amount | Rejection (PAY_4001) | Verify limit policy |

      ## 6. Response & Error Mapping
      | Error Code / Log Signature | Result | Possible Causes | How to Confirm | Support Action |
      | PAY_4001 | Payment Rejected | Daily limit exceeded | Check account ledger | Advise customer |

      ## 7. Data Origin, Reference Data & Transformation
      Account BIN ranges loaded from cache.

      ## 8. Transaction Lifecycle, States & Recovery
      From INITIATED to COMPLETED or FAILED. Auto-reconciliation worker resolves pending status.

      ## 9. Downstream Dependencies & Response Interpretation
      | Dependency | Purpose | Timeout | Automatic Failure Handling | Failure Propagation | Support Check |
      | CBS | Core Banking | 2000ms | Retry twice | Returns 503 | Check CBS status |

      ## 10. Datastore Support Evidence
      | Table | Access | Lookup Key | Support-Relevant Fields | Operational Purpose |
      | payments | READ_WRITE | payment_id | status, amount, created_at | Records transactions |

      ## 11. Kafka & Asynchronous Processing
      | Topic | Direction | Consumer Group | Business Purpose | Failure Handling |
      | payment-events | OUTBOUND | N/A | Notification stream | Retry queue |

      ## 12. Resilience & Automatic Failure Handling
      Circuit breakers configured with resilience4j on CBS client.

      ## 13. Rate Limiting, Capacity & Concurrency
      Rate limit: 1000 requests/sec per API key.

      ## 14. Data Retention, Expiry & Archival
      Transaction records retained for 7 years as per banking regulations.

      ## 15. Idempotency & Duplicate Protection
      Idempotency key header enforced via Redis lock.

      ## 16. Deployment / Schema Compatibility
      Flyway migrations manage database DDL. Zero-downtime rolling deployments.

      ## 17. Operational Errors & Troubleshooting
      | Error Code / Log Signature | Result | Possible Causes | How to Confirm | Support Action |
      | DB_CONN_TIMEOUT | Error 500 | Connection pool exhausted | Grep logs for HikariPool | Check DB connections |

      ## 18. Alerts / Support Health Checks & Monitoring
      Prometheus metrics exposed on /actuator/prometheus.

      ## 19. Transaction Tracing
      X-Correlation-ID header propagated to downstream calls.

      ## 20. Unknown / Unclassified Incident Triage
      1. Check actuator health endpoint. 2. Verify downstream latency. 3. Review error log spikes.

      ## 21. Support Responsibility & Access
      L1/L2 do not replay events, change offsets, mutate production data, or change runtime/configuration.

      ## 22. Escalation Evidence Checklist
      Collect transaction ID, correlation ID, error stack trace, and downstream payload.

      ## 23. Pipeline & Generation Metadata
      Generated in LEAN mode. Commit: develop-head-sha-9999.
      """;

  private final AtomicInteger callCount = new AtomicInteger(0);

  public int getCallCount() {
    return callCount.get();
  }

  public void resetCallCount() {
    callCount.set(0);
  }

  @Bean
  @Primary
  public RunbookAiClient fakeAiClient() {
    return prompt -> {
      callCount.incrementAndGet();
      String serviceId = "payments-service";
      Matcher m = Pattern.compile("- Service ID:\\s*([\\w.\\-]+)").matcher(prompt);
      if (m.find()) {
        serviceId = m.group(1).trim();
      }
      return VALID_SAMPLE_RUNBOOK_MD.replace("payments-service", serviceId);
    };
  }
}
