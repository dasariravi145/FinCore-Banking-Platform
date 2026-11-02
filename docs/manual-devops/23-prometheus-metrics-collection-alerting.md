# FinCore Banking Platform — DevOps Engineering Manual
## Manual 23: Prometheus Metrics Collection, Micrometer Scraping & Banking Alert Rules

### A. Objective & Architecture Context
A core banking platform cannot operate as a black box. SRE teams require continuous, high-resolution metric collection to measure the "Four Golden Signals" (Latency, Traffic, Errors, and Saturation) alongside critical banking business metrics (such as transfer failure rates, double-entry ledger balance drift, and database connection pool saturation). This manual configures Prometheus to scrape Spring Boot Actuator via Micrometer and implements production alert rules in `devops-configurations/monitoring/alert-rules.yaml`.

### B. Prerequisites & Dependencies
- Backend pod running and exposing `/actuator/prometheus` (Manual 18).
- Prometheus Operator or standalone Prometheus installed in `monitoring` namespace.
- Prometheus configuration references in `devops-configurations/monitoring/`.

### C. Step-by-Step Implementation Instructions

#### Step 1: Verify Spring Boot Actuator Prometheus Endpoint
Query the backend Actuator Prometheus metrics endpoint:
```powershell
kubectl exec -n fincore deploy/fincore-backend -- wget -qO- http://localhost:8080/actuator/prometheus | grep "fincore"
```
Verify that custom banking metrics are exposed:
- `fincore_transfers_total`
- `fincore_transfers_failed_total`
- `fincore_ledger_imbalance_ratio`
- `hikaricp_connections_active`
- `hikaricp_connections_pending`

#### Step 2: Review Prometheus Scraping Configuration
Inspect `devops-configurations/monitoring/prometheus.yml`:
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "alert-rules.yaml"

scrape_configs:
  - job_name: "fincore-backend"
    metrics_path: "/actuator/prometheus"
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - fincore
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: fincore-backend
      - source_labels: [__address__]
        action: replace
        regex: ([^:]+)(?::\d+)?
        replacement: ${1}:8080
        target_label: __address__
```

#### Step 3: Inspect FinCore Banking Alert Rules
Review `devops-configurations/monitoring/alert-rules.yaml`:
1. **`LedgerImbalanceDetected` (SEV-1 Critical):**
   ```yaml
   alert: LedgerImbalanceDetected
   expr: fincore_ledger_imbalance_ratio > 0
   for: 0m
   labels:
     severity: critical
     tier: financial-core
   annotations:
     summary: "CRITICAL: Double-entry ledger imbalance detected in FinCore database!"
     description: "Net ledger imbalance is non-zero. System reserve or transfer entries are out of balance. Trigger immediate Sev-1 incident."
   ```
2. **`HighTransferFailureRate` (SEV-2 High):**
   ```yaml
   alert: HighTransferFailureRate
   expr: (sum(rate(fincore_transfers_total{status="FAILED"}[5m])) / sum(rate(fincore_transfers_total[5m]))) > 0.05
   for: 2m
   labels:
     severity: warning
   annotations:
     summary: "Transfer failure rate exceeded 5% over the last 5 minutes."
   ```
3. **`HikariCPPoolExhaustion` (SEV-2 High):**
   ```yaml
   alert: HikariCPPoolExhaustion
   expr: (hikaricp_connections_pending{pool="HikariPool-1"} > 0) and (hikaricp_connections_active{pool="HikariPool-1"} >= 10)
   for: 1m
   labels:
     severity: warning
   annotations:
     summary: "Database connection pool saturated; threads are waiting for connections."
   ```
4. **`ServiceDown` (SEV-1 Critical):**
   ```yaml
   alert: FinCoreBackendDown
   expr: up{job="fincore-backend"} == 0
   for: 1m
   labels:
     severity: critical
   ```

#### Step 4: Apply Rules to Prometheus
Deploy the rules ConfigMap or PrometheusRule CRD to the `monitoring` namespace:
```powershell
kubectl apply -f devops-configurations/monitoring/alert-rules.yaml -n monitoring
```

### D. Configuration References
- Prometheus Config: `devops-configurations/monitoring/prometheus.yml`
- Alert Rules: `devops-configurations/monitoring/alert-rules.yaml`
- Actuator Config: `src/main/resources/application.yml`

### E. Verification & Validation Steps
Port-forward Prometheus UI:
```powershell
kubectl port-forward svc/prometheus-k8s -n monitoring 9090:9090
```
Open `http://localhost:9090` in your browser:
- Navigate to **Status -> Targets**: Confirm `fincore-backend` target is `UP` (1/1 green).
- Navigate to **Alerts**: Confirm that all 4 alert rules are listed and in `INACTIVE` state.
- Query Expression: `rate(http_server_requests_seconds_count[1m])` to confirm live request rate graph.

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Prometheus target status shows `DOWN` with `context deadline exceeded`**
   - *Root Cause:* Kubernetes `NetworkPolicy` in `fincore` namespace is blocking ingress on port 8080 from the `monitoring` namespace.
   - *Remediation:* Verify that `networkpolicy.yaml` has an ingress rule allowing traffic from namespace labeled `kubernetes.io/metadata.name: monitoring`.
2. **Failure Mode: `/actuator/prometheus` returns 404 Not Found**
   - *Root Cause:* Spring Boot Actuator was not configured to expose the Prometheus endpoint.
   - *Remediation:* Ensure `management.endpoints.web.exposure.include: health,info,prometheus` is declared in `application.yml`.
3. **Failure Mode: Metric names missing in Prometheus**
   - *Root Cause:* Metrics are only generated after the first request passes through the code path.
   - *Remediation:* Execute a test transfer or login to trigger counter initialization.

### G. Rollback & Disaster Recovery Procedures
If Prometheus encounters disk full errors due to high metric churn:
1. Reduce metric retention period: `--storage.tsdb.retention.time=15d`.
2. Drop high-cardinality metrics (e.g. per-user UUID labels) using Prometheus `metric_relabel_configs`.

### H. Security & Compliance Guardrails
- Prometheus metrics must NEVER expose Sensitive Personal Information (SPI) or customer account numbers as metric labels (which creates massive label cardinality and leaks financial data into metric storage).
- Prometheus endpoints should be accessible only from internal cluster networks.

### I. Production vs Local Differences

| Dimension | Local Docker Testing | Production AWS EKS |
| :--- | :--- | :--- |
| **Collector Mode** | Single Prometheus container | High-Availability Prometheus Operator with Thanos / Cortex |
| **Storage Backend** | Ephemeral local disk | AWS EBS gp3 persistent volumes with S3 long-term tiering |
| **Alert Routing** | Local terminal logs | PagerDuty, OpsGenie, and Slack webhook routing via AlertManager |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why is high label cardinality dangerous in Prometheus?**
  - *Answer:* In Prometheus, every unique combination of key-value labels creates a distinct time-series stream stored in RAM and inverted index memory. If a developer includes high-cardinality values like customer UUIDs, transfer IDs, or account numbers as metric labels, millions of time series are generated within hours. This leads to Prometheus Out-Of-Memory (OOM) crashes, slow query evaluations, and degraded monitoring availability.
- **Q2: Explain the difference between `rate()` and `irate()` in PromQL.**
  - *Answer:* `rate()` calculates the per-second average rate of increase across a specified time window (e.g. `[5m]`). It smooths out momentary spikes and handles counter resets cleanly, making it ideal for alerting rules. `irate()` calculates the instantaneous per-second rate based strictly on the last two data points under the window; it captures extreme transient micro-spikes but is too noisy for stable alert thresholds.
- **Q3: How do you design an alert for financial ledger imbalance?**
  - *Answer:* A financial ledger imbalance alert must have `for: 0m` (immediate alert evaluation with zero soak time) and `severity: critical`. Unlike a temporary CPU spike that can wait 5 minutes, any non-zero value on `fincore_ledger_imbalance_ratio` indicates that debits do not equal credits in the double-entry accounting ledger. This requires immediate automated transaction suspension and high-priority on-call pager dispatch.

### K. Cost & Performance Considerations
- 15-second scrape interval provides sub-minute anomaly detection with minimal CPU impact (< 1% overhead on Spring Boot backend).

### L. Real-World Banking Considerations
- Financial institutions are required by regulators to monitor service availability metrics and report major operational incidents (such as core banking outages exceeding 5 minutes) to central banks under mandatory incident reporting guidelines.

### M. Progress Sign-off Checklist
- [ ] `/actuator/prometheus` scraping verified.
- [ ] Target status `UP` confirmed in Prometheus UI.
- [ ] `alert-rules.yaml` loaded.
- [ ] Zero-drift ledger alert verified active.

### N. Next Manual Transition
Proceed to **[Manual 24: Grafana Dashboards & Banking KPI Visualization](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/24-grafana-dashboards-banking-visualization.md)** to visualize Golden Signals and transaction throughput.

