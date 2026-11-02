# FinCore Banking Platform — DevOps Engineering Manual
## Manual 27: SRE SLIs, SLOs, Error Budget Burn Rates & Incident Runbooks

### A. Objective & Architecture Context
Site Reliability Engineering (SRE) bridges software development and systems operations through quantitative reliability engineering. In a core banking platform, 100% uptime is mathematically impossible and economically prohibitive; instead, we establish clear Service Level Indicators (SLIs), Service Level Objectives (SLOs), and Error Budgets defined in `devops-configurations/sre/slos.yaml`. When error budgets burn too rapidly, feature deployments halt and engineering focus shifts strictly to platform reliability. This manual establishes the SRE framework and provides actionable Sev-1 runbooks.

### B. Prerequisites & Dependencies
- Manuals 23–26 completed (Prometheus metrics, Grafana dashboards, Loki logs, and Tempo traces operational).
- Understanding of Prometheus AlertManager routing.

### C. Step-by-Step Implementation Instructions

#### Step 1: Inspect the FinCore SLO Architecture
Review `devops-configurations/sre/slos.yaml`:
1. **SLO 1: Core Banking API Availability (99.95% over rolling 30 days):**
   - **SLI:** Ratio of successful non-5xx requests to total HTTP requests.
   - **Error Budget:** 0.05% permitted failures (~21.6 minutes of downtime per 30 days).
   - **PromQL:**
     ```promql
     sum(rate(http_server_requests_seconds_count{status!~"5.."}[30d])) 
     / 
     sum(rate(http_server_requests_seconds_count[30d])) >= 0.9995
     ```
2. **SLO 2: Transfer Transaction Latency (99.0% < 500ms over rolling 30 days):**
   - **SLI:** Percentage of transfer requests completing in under 500 milliseconds.
   - **PromQL:**
     ```promql
     sum(rate(http_server_requests_seconds_bucket{uri=~"/api/v1/transfers.*", le="0.5"}[30d])) 
     / 
     sum(rate(http_server_requests_seconds_count{uri=~"/api/v1/transfers.*"}[30d])) >= 0.99
     ```
3. **SLO 3: Financial Ledger Integrity (100.0% Absolute Invariant):**
   - **SLI:** Zero net imbalance across all historical double-entry ledger postings.
   - **Error Budget:** Exactly 0.00% (Zero tolerance for unbacked money creation).

#### Step 2: Implement Multiwindow, Multi-Burn-Rate Alerting
Traditional threshold alerts fire too late or trigger false alarms. FinCore uses Google SRE Multiwindow Burn-Rate alerts:
- **1-Hour Burn Rate = 14.4 (Page On-Call Immediately):** Consumes 2% of the 30-day error budget in 1 hour. Indicates a massive active outage.
- **6-Hour Burn Rate = 6.0 (Page On-Call):** Consumes 5% of the 30-day error budget in 6 hours.
- **24-Hour Burn Rate = 3.0 (Ticket/Slack Notification):** Consumes 10% of the budget in 24 hours.

#### Step 3: Master Sev-1 Incident Runbooks

##### RUNBOOK 01: Sev-1 Ledger Imbalance Detected
- **Trigger:** Alert `LedgerImbalanceDetected` fires (`fincore_ledger_imbalance_ratio > 0`).
- **Severity:** P0 / Sev-1 (Critical Business Impact).
- **Step 1: Triage & Communication:** Declare Sev-1 incident room in Slack/Teams; notify Head of Core Banking and Compliance Officer.
- **Step 2: Emergency Containment:** If necessary, toggle read-only mode in backend ConfigMap to suspend new transfer postings:
  ```powershell
  kubectl patch configmap fincore-backend-config -n fincore --type merge -p '{"data":{"FINCORE_MAINTENANCE_MODE":"true"}}'
  ```
- **Step 3: Run Database Reconciliation Audit:**
  ```powershell
  kubectl exec -n fincore deploy/fincore-backend -- psql $DB_URL -c "
  SELECT transfer_request_id, COUNT(*), SUM(CASE WHEN entry_type='DEBIT' THEN amount ELSE -amount END) AS diff
  FROM ledger_entries
  GROUP BY transfer_request_id
  HAVING SUM(CASE WHEN entry_type='DEBIT' THEN amount ELSE -amount END) != 0;"
  ```
- **Step 4: Root Cause & Resolution:** Identify if an unhandled exception bypassed `@Transactional` rollback. Post compensating balancing adjustment entry with compliance approval.

##### RUNBOOK 02: Database Connection Pool (HikariCP) Exhaustion
- **Trigger:** Alert `HikariCPPoolExhaustion` fires.
- **Step 1: Inspect Active Database Locks:**
  ```powershell
  kubectl exec -n fincore deploy/fincore-backend -- psql $DB_URL -c "
  SELECT pid, now() - query_start AS duration, query, state, wait_event_type, wait_event 
  FROM pg_stat_activity 
  WHERE state != 'idle' ORDER BY duration DESC LIMIT 5;"
  ```
- **Step 2: Terminate Blocking Query:**
  ```sql
  SELECT pg_terminate_backend(<BLOCKING_PID>);
  ```
- **Step 3: Temporary Relief:** Increase `maximumPoolSize` in backend ConfigMap and restart pods gracefully.

### D. Configuration References
- SRE SLO Manifest: `devops-configurations/sre/slos.yaml`
- Alert Rules: `devops-configurations/monitoring/alert-rules.yaml`
- Grafana Dashboard: `devops-configurations/monitoring/grafana-dashboard.json`

### E. Verification & Validation Steps
Verify that error budget burn rate metrics evaluate cleanly in Prometheus:
```powershell
curl -s "http://localhost:9090/api/v1/query?query=fincore_ledger_imbalance_ratio" | jq .data.result
```
*Expected Output:* Value is `0`.

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Burn rate alert fires during planned load testing**
   - *Root Cause:* High volume of synthetic test failures burned the live SLO error budget.
   - *Remediation:* Create silence in AlertManager for the planned maintenance window, or route load test traffic with header `X-Test-Traffic: true` and exclude from production SLO queries.
2. **Failure Mode: Error budget exhausted (0% remaining) midway through sprint**
   - *Root Cause:* Frequent production regressions or network blips burned allowable failure budget.
   - *Remediation:* Enforce the SRE Error Budget Policy: halt feature deployments for the remaining sprint; allocate 100% of engineering bandwidth to automated testing, bug fixes, and infrastructure stabilization.
3. **Failure Mode: Runbook instructions fail due to missing CLI permissions**
   - *Root Cause:* On-call engineer's IAM role lacks `kubectl exec` permissions in production.
   - *Remediation:* Test runbook procedures quarterly in staging environments using standard on-call break-glass roles.

### G. Rollback & Disaster Recovery Procedures
Every production change must include a documented rollback plan. If an SLO burn rate alert triggers within 15 minutes of a deployment, the default action is immediate automated rollback via Argo CD.

### H. Security & Compliance Guardrails
- **Post-Mortem Culture:** Every Sev-1 or Sev-2 incident requires a blameless post-mortem published within 48 hours documenting root cause, timeline, customer impact, and preventative action items.
- **Audit Logging of Break-Glass Actions:** Any execution of emergency database scripts must be recorded in the security audit log.

### I. Production vs Local Differences

| Dimension | Local Staging Testing | Production SRE Operations |
| :--- | :--- | :--- |
| **SLO Horizon** | 1-hour evaluation window | Rolling 30-day window with historical error budget trends |
| **Alert Routing** | Local terminal logs | 24/7 on-call pager rotation (PagerDuty / OpsGenie) |
| **Incident Protocol** | Informal debugging | Formal Incident Commander, Scribe, and Communications leads |

### J. Interview Readiness Questions & Model Answers
- **Q1: What is an Error Budget and how does it balance feature velocity with system reliability?**
  - *Answer:* An Error Budget is the inverse of an SLO (e.g. an SLO of 99.9% provides a 0.1% error budget). It represents the acceptable amount of unreliability a service can experience without violating customer expectations. If the error budget is healthy (> 50%), development teams can push features rapidly and take calculated deployment risks. If the error budget is exhausted (0%), all feature launches are frozen, and the entire team focuses exclusively on refactoring, technical debt reduction, and reliability engineering until the budget recovers.
- **Q2: Why are Multi-Window Multi-Burn-Rate alerts superior to simple percentage threshold alerts?**
  - *Answer:* Simple threshold alerts (e.g. "alert if error rate > 1%") create alert fatigue by paging on brief 10-second blips that burn almost no error budget, while failing to detect slow, continuous 0.1% error leaks that completely exhaust the monthly budget over two weeks. Multi-window burn-rate alerts measure the *speed* of budget consumption across short (1 hour) and long (6 hour) windows, ensuring engineers are paged only when an issue is severe enough to significantly threaten the monthly SLO.
- **Q3: Describe a Blameless Post-Mortem in a banking context.**
  - *Answer:* A blameless post-mortem assumes that engineers act in good faith with the best intentions and information available at the time. Instead of blaming human error (e.g. "Engineer John mistyped a command"), we analyze systemic and procedural vulnerabilities (e.g. "Why did the system permit an unvalidated command to execute on production without a two-person review gate?"). The goal is hardening automated guardrails, updating runbooks, and adding test suites to make future failures structurally impossible.

### K. Cost & Performance Considerations
- Multiwindow SLO metric evaluation in Prometheus uses minimal TSDB resources by evaluating over pre-aggregated recording rules.

### L. Real-World Banking Considerations
- Financial institutions must maintain detailed records of operational risk events under Basel III Pillar 2 capital adequacy requirements. Formal SRE incident post-mortems provide documented evidence of institutional risk controls.

### M. Progress Sign-off Checklist
- [ ] `slos.yaml` defined and imported into Prometheus.
- [ ] Core Banking Availability SLO (99.95%) active.
- [ ] Multiwindow burn rate alerting rules active.
- [ ] Sev-1 Runbooks reviewed and rehearsed.

### N. Next Manual Transition
Proceed to **[Manual 28: DevSecOps Secrets Management with HashiCorp Vault](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/28-devsecops-vault-secrets-management.md)** to begin Part 6 (Secrets Management, MLOps, AIOps, FinOps & Portfolio Presentation).

