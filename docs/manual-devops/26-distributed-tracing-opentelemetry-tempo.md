# FinCore Banking Platform — DevOps Engineering Manual
## Manual 26: Distributed Tracing with OpenTelemetry, Tempo & W3C Context Propagation

### A. Objective & Architecture Context
When a customer clicks "Submit Transfer" in the FinCore frontend, the transaction traverses multiple distinct network boundaries: the browser, the AWS Application Load Balancer, the Nginx reverse proxy, Spring Security filter chains, the transfer business logic service, the double-entry ledger posting engine, and the PostgreSQL database driver. When a transfer takes 2.8 seconds, metrics and logs can indicate *that* it was slow, but cannot isolate *which specific component* caused the delay. Distributed tracing records timing spans across the entire request lifecycle using OpenTelemetry and Grafana Tempo based on `devops-configurations/tracing/otel-collector-config.yaml`.

### B. Prerequisites & Dependencies
- Backend running with Spring Boot 3 Micrometer Tracing enabled.
- OpenTelemetry Collector deployed in cluster or running locally.
- Grafana Tempo running on port 3200 (locally or in Kubernetes `tracing` namespace).

### C. Step-by-Step Implementation Instructions

#### Step 1: Inspect OpenTelemetry Collector Architecture
Review `devops-configurations/tracing/otel-collector-config.yaml`:
- **Receivers:**
  - `otlp.protocols.grpc`: Listening on port `4317`.
  - `otlp.protocols.http`: Listening on port `4318`.
- **Processors:**
  - `memory_limiter`: Drops spans or applies backpressure if collector memory exceeds 400 MiB.
  - `batch`: Groups trace spans into 512-span batches or flushes every 1 second to minimize network overhead.
- **Exporters:**
  - `otlp/tempo`: Forwards spans over gRPC to Grafana Tempo (`tempo:4317`).
  - `logging`: Dumps trace metadata for debugging.

#### Step 2: Configure Spring Boot Backend OpenTelemetry Agent
Spring Boot 3 uses Micrometer Tracing. In `pom.xml`, the following dependencies provide automated OTel instrumentation:
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```
In `src/main/resources/application.yml`:
```yaml
management:
  tracing:
    sampling:
      probability: 1.0 # 100% trace sampling for test/staging; 0.1 (10%) for prod
  otlp:
    tracing:
      endpoint: "http://otel-collector.tracing.svc:4318/v1/traces"
```

#### Step 3: Deploy OpenTelemetry Collector & Tempo
Create namespace and deploy collector:
```powershell
kubectl create namespace tracing
kubectl apply -f devops-configurations/tracing/otel-collector-config.yaml -n tracing
```

#### Step 4: Add Tempo Datasource in Grafana
1. In Grafana, navigate to **Configuration -> Data Sources -> Add data source** -> **Tempo**.
2. URL: `http://tempo.tracing.svc:3200` (or `http://localhost:3200`).
3. Under **Trace to logs**, configure correlation with Loki datasource using tag `trace_id`.
4. Click **Save & Test**.

### D. Configuration References
- Collector Config: `devops-configurations/tracing/otel-collector-config.yaml`
- Application Config: `src/main/resources/application.yml`
- Logging correlation: `src/main/resources/logback-spring.xml` (includes `[${traceId:-},${spanId:-}]` in pattern)

### E. Verification & Validation Steps
1. Execute a simulated transfer API call via curl or frontend portal:
   ```powershell
   $body = @{
       sourceAccountId = "acc-001"
       targetAccountId = "acc-002"
       amount = 50.00
       currency = "USD"
       description = "Tracing Verification"
   } | ConvertTo-Json

   Invoke-RestMethod -Uri "http://localhost:8080/api/v1/transfers" `
     -Method Post -Body $body -ContentType "application/json" `
     -Headers @{ Authorization = "Bearer $token" }
   ```
2. Open Grafana **Explore** tab -> Select **Tempo**.
3. Search for recent traces for service `fincore-backend`.
4. Open the trace:
   - Root span: `POST /api/v1/transfers` (Total duration: 42ms)
   - Child span: `TransferService.initiateTransfer` (Duration: 38ms)
   - Child span: `AccountRepository.findWithPessimisticLock` (Duration: 8ms)
   - Child span: `LedgerService.postDoubleEntry` (Duration: 12ms)
   - Child span: `NotificationService.dispatch` (Duration: 4ms)

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Traces do not appear in Tempo**
   - *Root Cause:* Spring Boot sampling probability is set to 0.0 or OTLP exporter endpoint is unreachable.
   - *Remediation:* Ensure `management.tracing.sampling.probability: 1.0` is active during testing and port 4318 is accessible to the backend pod.
2. **Failure Mode: Broken trace context between frontend and backend**
   - *Root Cause:* Reverse proxy (Nginx) stripped the standard `traceparent` HTTP header.
   - *Remediation:* Ensure Nginx configuration includes `proxy_pass_request_headers on;` or explicitly forwards `proxy_set_header traceparent $http_traceparent;`.
3. **Failure Mode: OTel Collector high memory utilization**
   - *Root Cause:* 100% trace sampling under high-volume load spikes.
   - *Remediation:* Configure tail-based sampling or probabilistic sampling (e.g. 5% of successful requests; 100% of error requests).

### G. Rollback & Disaster Recovery Procedures
Distributed tracing is an out-of-band telemetry tool. If the OTel Collector or Tempo crashes, Spring Boot's non-blocking exporter safely drops spans without degrading or blocking live banking transactions.

### H. Security & Compliance Guardrails
- **Zero Sensitive Data in Span Attributes:** Tracing spans must record HTTP routes (`/api/v1/transfers`), method (`POST`), and status code (`200`), but must NEVER record customer account passwords, credit card numbers, or Social Security numbers as span tags.

### I. Production vs Local Differences

| Dimension | Local Developer Tracing | Production Cloud Tracing |
| :--- | :--- | :--- |
| **Sampling Rate** | 100% (`probability: 1.0`) | Probabilistic (5%–10%) with 100% error-path sampling |
| **Storage Engine** | Local in-memory Tempo block | Scalable object storage in Amazon S3 |
| **Trace Retention** | 24 hours | 14 to 30 days |

### J. Interview Readiness Questions & Model Answers
- **Q1: How does W3C TraceContext propagation work across distributed microservices?**
  - *Answer:* The W3C TraceContext standard defines two HTTP headers: `traceparent` and `tracestate`. The `traceparent` header carries a 4-part hyphen-separated string: `version-trace_id-parent_id-trace_flags` (e.g. `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`). When a client or service invokes downstream APIs, it forwards this header. Every downstream component adopts the `trace_id` and generates a new `span_id` referencing the incoming `parent_id`, allowing a centralized collector to reconstruct the entire distributed call tree.
- **Q2: Why is tail-based sampling superior to head-based sampling in high-volume banking systems?**
  - *Answer:* Head-based sampling makes the sampling decision at the very start of a request (e.g. randomly keeping 5% of requests). If a critical 500 error or database deadlock occurs on an unsampled request, the trace is permanently lost. Tail-based sampling buffers all spans in the collector until the entire transaction completes; it can then evaluate the outcome and guarantee that 100% of slow (> 1s) or failing (HTTP 5xx) transactions are preserved while discarding boring, fast 200 OK traces.
- **Q3: How do you achieve "Trace-to-Log" correlation in Grafana?**
  - *Answer:* We inject the active `traceId` and `spanId` into the logging Mapped Diagnostic Context (MDC) via Logback pattern `%X{traceId:-}`. When Promtail ingests the log into Loki, Loki extracts this ID. In Grafana, configuring a "Trace to logs" data link allows an engineer viewing a slow span in Tempo to click a button and jump directly to the exact application log lines emitted by that specific thread during that span's execution window.

### K. Cost & Performance Considerations
- Tempo's object-storage architecture eliminates expensive indexing, cutting tracing storage costs by up to 90% compared to legacy Elasticsearch-backed Jaeger deployments.

### L. Real-World Banking Considerations
- Financial transactions spanning multiple partner networks (ACH, SWIFT, card processors) rely on unique end-to-end trace IDs to locate missing payments during cross-institution reconciliation disputes.

### M. Progress Sign-off Checklist
- [ ] OTel Collector and Tempo active.
- [ ] Backend emitting OTLP spans to collector.
- [ ] Tempo datasource connected in Grafana.
- [ ] Multi-span transfer trace visualized and verified.

### N. Next Manual Transition
Proceed to **[Manual 27: SRE SLIs, SLOs, Error Budgets & Production Runbooks](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/27-sre-sli-slo-error-budgets-runbooks.md)** to establish formal service level objectives and incident response procedures.

