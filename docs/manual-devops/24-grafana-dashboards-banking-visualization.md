# FinCore Banking Platform — DevOps Engineering Manual
## Manual 24: Grafana Dashboards, Golden Signals & Banking KPI Visualization

### A. Objective & Architecture Context
Raw Prometheus metrics and alert expressions are difficult for cross-functional engineering, SRE, and product operations teams to synthesize during live production incidents. Grafana provides unified, real-time visualization of system health. This manual provisions an enterprise banking operations dashboard (`devops-configurations/monitoring/grafana-dashboard.json`) visualizing SRE Golden Signals, JVM internals, and banking business KPIs.

### B. Prerequisites & Dependencies
- Manual 23 completed (Prometheus scraping Spring Boot metrics).
- Grafana 10.x running (locally on port 3000 or inside EKS `monitoring` namespace).
- Prometheus configured as the default datasource in Grafana.

### C. Step-by-Step Implementation Instructions

#### Step 1: Inspect the Dashboard Specification Architecture
Review `devops-configurations/monitoring/grafana-dashboard.json`:
1. **Row 1: Executive Banking KPIs:**
   - **Net Ledger Imbalance:** Real-time Stat gauge displaying `fincore_ledger_imbalance_ratio` (Green at 0.00; Bright Red if > 0).
   - **Transaction Throughput:** Time series of transfers per second broken down by status (`SUCCESS`, `FAILED`, `PENDING`).
   - **Total Loan Portfolio Value:** Stat panel displaying active loan volume.
2. **Row 2: The SRE Four Golden Signals:**
   - **Latency:** Heatmap and percentile lines for HTTP p50, p95, and p99 response times.
   - **Traffic:** Total incoming HTTP requests per second (`http_server_requests_seconds_count`).
   - **Errors:** Ratio of HTTP 5xx responses to total requests.
   - **Saturation:** JVM Heap memory usage vs Max allocated heap.
3. **Row 3: Infrastructure & Persistence Layer:**
   - **HikariCP Connection Pool:** Stacked graph of `hikaricp_connections_active`, `idle`, and `pending`.
   - **Garbage Collection Pauses:** JVM GC pause duration and frequency (`jvm_gc_pause_seconds_sum`).
   - **Pod CPU & Memory Utilization:** Kubernetes cgroup resource usage against defined limits.
4. **Row 4: Fraud Detection Telemetry:**
   - **Rule Evaluation Outcomes:** Pie chart of transactions categorized as `APPROVED`, `FLAGGED_REVIEW`, or `REJECTED`.
   - **Triggered Rules Breakdown:** Bar chart identifying top tripped rules (`HighValueTransferRule`, `RapidVelocityRule`).

#### Step 2: Import the Dashboard into Grafana
1. Open Grafana in your browser (`http://localhost:3000`).
2. Log in with admin credentials (`admin` / `admin`).
3. Ensure Prometheus datasource is connected:
   - Navigate to **Configuration -> Data Sources -> Add data source** -> **Prometheus**.
   - URL: `http://prometheus-k8s.monitoring.svc:9090` (or `http://localhost:9090`).
   - Click **Save & Test**.
4. Import Dashboard:
   - Navigate to **Dashboards -> New -> Import**.
   - Click **Upload JSON file** -> Select `devops-configurations/monitoring/grafana-dashboard.json`.
   - Select the Prometheus datasource -> Click **Import**.

### D. Configuration References
- Dashboard JSON: `devops-configurations/monitoring/grafana-dashboard.json`
- Alert Rules: `devops-configurations/monitoring/alert-rules.yaml`
- Actuator Metrics: `src/main/resources/application.yml`

### E. Verification & Validation Steps
Navigate to the newly imported **FinCore Banking Platform — Executive & SRE Overview** dashboard:
- Confirm that all panels load without PromQL query errors.
- Stat panel for **Net Ledger Imbalance** shows `0.00` in green.
- Perform a transfer in the FinCore frontend; witness the instantaneous spike in the **Transaction Throughput** graph.
- Verify that JVM Heap utilization is accurately tracked within the 512MiB boundary.

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Dashboard displays `No Data` across all panels**
   - *Root Cause:* The Grafana datasource name in the JSON template (e.g. `Prometheus`) does not match your configured datasource name.
   - *Remediation:* Open Dashboard Settings -> Variables -> Datasource, and select your active Prometheus instance.
2. **Failure Mode: `http_server_requests_seconds_bucket` returns empty**
   - *Root Cause:* Spring Boot's `@Timed` annotations or default metric filters are disabled.
   - *Remediation:* Verify that `management.metrics.web.server.request.autotime.enabled=true` is present in backend configuration.
3. **Failure Mode: Grafana dashboard resets after pod restart**
   - *Root Cause:* Grafana was deployed without a persistent volume or without GitOps ConfigMap provisioning.
   - *Remediation:* In Kubernetes, use the Grafana Dashboard Sidecar (`grafana-sc-dashboard`) to automatically load dashboards from ConfigMaps labeled `grafana_dashboard: "1"`.

### G. Rollback & Disaster Recovery Procedures
To preserve custom dashboard modifications:
1. Export the dashboard JSON via **Share -> Export -> Save to file**.
2. Commit the updated JSON directly into `devops-configurations/monitoring/grafana-dashboard.json` in Git.

### H. Security & Compliance Guardrails
- Grafana must enforce Role-Based Access Control (Viewer, Editor, Admin). Read-only Viewer roles are assigned to support desks.
- Sensitive financial metrics (e.g. balance distributions) must be masked or restricted to authorized finance roles.

### I. Production vs Local Differences

| Dimension | Local Grafana Container | Production Enterprise Grafana |
| :--- | :--- | :--- |
| **Authentication** | Local admin password | Enterprise SSO via Okta / Azure AD / AWS IAM Identity Center |
| **Persistence** | Ephemeral or local SQLite | Managed PostgreSQL database for dashboard and user metadata |
| **High Availability** | Single instance | Multi-pod deployment fronted by an Application Load Balancer |

### J. Interview Readiness Questions & Model Answers
- **Q1: What are the Google SRE "Four Golden Signals" and how do they apply to FinCore?**
  - *Answer:* (1) **Latency:** Time taken to service banking requests (tracking p99 transfer latency < 500ms). (2) **Traffic:** Demand placed on the platform (HTTP requests/second and transfer volume/second). (3) **Errors:** Rate of failing requests (HTTP 5xx errors and transaction rollback ratios). (4) **Saturation:** How full the most constrained system resources are (HikariCP connection pool usage and JVM heap memory).
- **Q2: Why is p99 latency more important than average latency in banking systems?**
  - *Answer:* Average (mean) latency hides catastrophic user experience. If 99 transfers complete in 10ms, but 1 transfer deadlocks for 10 seconds, the average latency is ~110ms (which appears acceptable on a dashboard). However, for the customer waiting 10 seconds, their transfer may time out or trigger duplicate submissions. Percentile metrics (p95, p99) expose tail latency bottlenecks and lock contention that averages conceal.
- **Q3: How do you provision Grafana dashboards in a pure GitOps workflow?**
  - *Answer:* We treat dashboards as code. The dashboard JSON is committed to Git inside the repository. Using the Prometheus Operator / Grafana Helm chart sidecar pattern, Kubernetes watches for ConfigMaps with label `grafana_dashboard: "1"`. When Argo CD syncs the repository, the sidecar automatically discovers the ConfigMap and hot-reloads the dashboard in Grafana with zero manual UI configuration.

### K. Cost & Performance Considerations
- Dashboard refresh rates should be set to 15s or 30s to avoid overwhelming Prometheus TSDB evaluate engines during high-concurrency dashboard viewing.

### L. Real-World Banking Considerations
- Financial institutions utilize Grafana wall displays in Network Operations Centers (NOC) and Security Operations Centers (SOC) for continuous situational awareness during high-volume trading and payment settlement periods.

### M. Progress Sign-off Checklist
- [ ] Prometheus datasource connected in Grafana.
- [ ] `grafana-dashboard.json` imported successfully.
- [ ] Golden Signals panels active and rendering live data.
- [ ] Net Ledger Imbalance verified at 0.00.

### N. Next Manual Transition
Proceed to **[Manual 25: Centralized Logging with Loki & Promtail](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/25-centralized-logging-loki-promtail.md)** to aggregate, index, and query distributed container logs.

