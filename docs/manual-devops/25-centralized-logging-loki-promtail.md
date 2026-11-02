# FinCore Banking Platform — DevOps Engineering Manual
## Manual 25: Centralized Logging with Grafana Loki, Promtail & LogQL Forensics

### A. Objective & Architecture Context
When microservices scale across multi-node Kubernetes clusters, troubleshooting production incidents by running `kubectl logs` on individual ephemeral pods is slow, uncoordinated, and fails when crashed pods are evicted. Financial compliance mandates that all banking operations, transfer authorizations, authentication attempts, and administrative actions be aggregated, tamper-evident, and searchable for years. This manual implements centralized logging using Grafana Loki and Promtail based on `devops-configurations/logging/`.

### B. Prerequisites & Dependencies
- Manual 18 completed (FinCore pods emitting structured logs to stdout).
- Grafana running (Manual 24).
- Kubernetes cluster with permissions to deploy DaemonSets in `logging` namespace.

### C. Step-by-Step Implementation Instructions

#### Step 1: Inspect Loki Storage Architecture
Review `devops-configurations/logging/loki-config.yaml`:
- **Architecture:** Loki does not index full log text like Elasticsearch; it indexes metadata labels (`app`, `namespace`, `pod`, `container`), drastically reducing index memory and storage footprint.
- **Chunk Storage:** Chunks stored in Amazon S3 or local persistent volume (`fincore-loki-chunks`).
- **Retention Period:** 90 days retention for active log queries; archival lifecycle policy to S3 Glacier for 7-year regulatory compliance.

#### Step 2: Inspect Promtail Log Collection & Scrubbing Pipeline
Review `devops-configurations/logging/promtail-config.yaml`:
- **Collection Daemon:** Deployed as a Kubernetes `DaemonSet` on every worker node to tail `/var/log/pods/*/*.log`.
- **Pipeline Stages:**
  1. **Docker / Containerd Parsing:** Unwraps JSON container runtime log wrappers.
  2. **PCI-DSS Log Sanitization (Masking):** Uses regex replacement to mask credit card PANs and account numbers (`\b\d{4}[ -]?\d{4}[ -]?\d{4}[ -]?\d{4}\b` -> `[CARD-MASKED]`).
  3. **Label Extraction:** Promotes `level`, `app`, and `transaction_type` to Loki stream labels.

#### Step 3: Deploy Loki and Promtail to Cluster
Create the `logging` namespace and apply manifests:
```powershell
kubectl create namespace logging
kubectl apply -f devops-configurations/logging/loki-config.yaml -n logging
kubectl apply -f devops-configurations/logging/promtail-config.yaml -n logging
```
Verify Promtail pods running on all worker nodes:
```powershell
kubectl get daemonset promtail -n logging
```

#### Step 4: Add Loki Datasource in Grafana
1. In Grafana, navigate to **Configuration -> Data Sources -> Add data source** -> **Loki**.
2. URL: `http://loki.logging.svc:3100` (or `http://localhost:3100`).
3. Click **Save & Test**.

### D. Configuration References
- Loki Configuration: `devops-configurations/logging/loki-config.yaml`
- Promtail Pipeline: `devops-configurations/logging/promtail-config.yaml`
- Backend Logback config: `src/main/resources/logback-spring.xml`

### E. Verification & Validation Steps
Open Grafana **Explore** tab and select the **Loki** datasource.
Execute the following LogQL queries:
1. **Stream All Backend Logs:**
   ```logql
   {namespace="fincore", app="fincore-backend"}
   ```
2. **Filter Critical Exceptions & Deadlocks:**
   ```logql
   {app="fincore-backend"} |~ "(?i)(Deadlock|LockTimeoutException|PSQLException|HikariPool)"
   ```
3. **Forensic Audit for a Specific Transfer:**
   ```logql
   {app="fincore-backend"} |= "TransferRequest" |= "COMPLETED"
   ```
4. **Log Rate Metric Aggregation:**
   ```logql
   sum by (level) (count_over_time({app="fincore-backend"}[1m]))
   ```

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Promtail cannot read log files (`Permission denied`)**
   - *Root Cause:* Node host `/var/log/pods` directory permissions require root access, but Promtail container is unprivileged.
   - *Remediation:* In Promtail DaemonSet pod template, configure `securityContext.privileged: true` or mount with appropriate host path volume permissions.
2. **Failure Mode: `entry too far behind` error in Loki logs**
   - *Root Cause:* Promtail ingested historical logs with timestamps older than Loki's `creation_grace_period` (default: 10m).
   - *Remediation:* Increase `creation_grace_period: 24h` in `loki-config.yaml` during initial historical log ingestions.
3. **Failure Mode: Out of memory in Loki during wide queries**
   - *Root Cause:* Querying unbounded regex over 30 days without stream label filters.
   - *Remediation:* Always specify at least one stream label (e.g. `{app="fincore-backend"}`) to narrow chunk retrieval before regex matching.

### G. Rollback & Disaster Recovery Procedures
If Loki persistent volume fills up:
1. Expand the underlying EBS volume via Terraform.
2. Purge chunks older than 14 days using the Loki compactor retention tool.

### H. Security & Compliance Guardrails
- **Data Sanitization at Collection:** Promtail regex masks customer passwords, tokens, and bank account numbers *before* logs are sent to Loki.
- **Append-Only Immutability:** Loki chunks written to AWS S3 can be protected with S3 Object Lock to prevent malicious log tampering or deletion.

### I. Production vs Local Differences

| Dimension | Local Docker Logs | Production Loki & S3 Cluster |
| :--- | :--- | :--- |
| **Storage Unit** | Ephemeral JSON files on developer host SSD | Scalable object storage in Amazon S3 with lifecycle rules |
| **Search Capability** | Simple terminal `grep` | High-performance LogQL stream filtering across 100+ pods |
| **Retention** | Overwritten when container is deleted | Indefinite archival meeting regulatory banking standards |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why choose Grafana Loki over the traditional Elasticsearch / OpenSearch (ELK) stack?**
  - *Answer:* Elasticsearch builds a full-text inverted index for every single word in every log line. This requires massive RAM, complex cluster management, and storage overhead often exceeding 150% of the raw log volume. Loki indexes only stream metadata labels (identical to Prometheus). Log text is stored as compressed chunks in cheap object storage (S3). This cuts infrastructure and compute costs by up to 80% while integrating natively with Grafana dashboards.
- **Q2: How do you enforce PCI-DSS data redaction in containerized logging pipelines?**
  - *Answer:* PCI-DSS Requirement 3.4 mandates that Primary Account Numbers (PANs) and sensitive authentication data must never appear in unencrypted logs. We implement a multi-layered redaction strategy: (1) Application layer: Custom Logback converters mask sensitive fields in DTO `toString()` representations. (2) Ingestion layer: Promtail pipeline regex stages scrub any remaining 16-digit card numbers or authentication tokens before streaming logs to the central aggregator.
- **Q3: What is the relationship between Prometheus metrics and Loki logs in Grafana?**
  - *Answer:* Prometheus and Loki share an identical label metadata schema (`app`, `pod`, `namespace`, `cluster`). In Grafana, when an SRE spots an anomalous latency spike on a Prometheus metric panel, they can split the screen and seamlessly jump directly into the exact Loki log stream for that specific pod and millisecond timestamp without re-entering search parameters.

### K. Cost & Performance Considerations
- Storing compressed log chunks in Amazon S3 costs ~$0.023 per GB-month, compared to $0.10+ per GB for EBS-backed Elasticsearch clusters.

### L. Real-World Banking Considerations
- Financial institutions are legally subject to regulatory discovery requests. Centralized immutable logging guarantees that forensic investigators can reconstruct every transfer attempt, balance inquiry, and administrative privilege change with microsecond timestamp fidelity.

### M. Progress Sign-off Checklist
- [ ] Loki and Promtail deployed in `logging` namespace.
- [ ] Promtail DaemonSet healthy on all nodes.
- [ ] LogQL queries returning structured backend logs in Grafana.
- [ ] Data redaction regex verified masking account numbers.

### N. Next Manual Transition
Proceed to **[Manual 26: Distributed Tracing with OpenTelemetry & Tempo](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/26-distributed-tracing-opentelemetry-tempo.md)** to trace end-to-end transaction paths across services.

