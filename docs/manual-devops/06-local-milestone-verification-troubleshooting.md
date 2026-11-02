# FinCore Banking Platform — DevOps Engineering Manual
## Manual 06: Local Milestone Verification, Banking Reconciliation & Troubleshooting

### A. Objective & Architecture Context
This manual serves as the mandatory validation gate for **Milestone 1**. Before writing CI/CD pipelines or provisioning AWS infrastructure, you must verify that the local multi-container banking platform is fully operational, health checks pass, authentication functions, simulated transfers complete successfully, and the double-entry financial ledger remains in 100% mathematical balance.

### B. Prerequisites & Dependencies
- Docker Compose stack active (`docker compose ps` shows all 3 services healthy).
- PowerShell execution privileges for running automated verification script.
- `curl` or Postman available for executing HTTP API calls.

### C. Step-by-Step Implementation Instructions

#### Step 1: Run the Automated Milestone Verifier
Execute the provided PowerShell test script from the project root:
```powershell
powershell -ExecutionPolicy Bypass -File devops-configurations/scripts/test-local-milestone.ps1
```
The script will perform 6 automated checks:
1. Checks host port 5435 (PostgreSQL).
2. Inspects Docker container health states.
3. Tests Spring Boot `/actuator/health`.
4. Validates Swagger UI documentation (`/swagger-ui/index.html`).
5. Tests Frontend Nginx web server (`http://localhost`).
6. Verifies database connection pool and Flyway migration status.

#### Step 2: Execute Manual API Authentication Test
Authenticate against the containerized backend:
```powershell
$loginBody = @{
    username = "admin"
    password = "AdminPassword123!"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" `
  -Method Post `
  -Body $loginBody `
  -ContentType "application/json"

$token = $response.accessToken
Write-Host "JWT Token acquired: $token"
```

#### Step 3: Execute Simulated Account & Transfer Verification
Query bank accounts using the acquired JWT:
```powershell
$accounts = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/accounts" `
  -Method Get `
  -Headers @{ Authorization = "Bearer $token" }

$accounts | Format-Table id, accountNumber, balance, currency, status
```

#### Step 4: Run Real-Time Double-Entry Ledger Reconciliation Audit
Connect into the containerized database and execute the audit query:
```powershell
docker exec -it fincore-db psql -U fincore_user -d fincore_db -c "
SELECT 
    COUNT(*) AS total_ledger_postings,
    COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END), 0) AS total_debits,
    COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END), 0) AS total_credits,
    COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE -amount END), 0) AS net_imbalance
FROM ledger_entries;"
```
*Expected Invariant:* `net_imbalance` must be strictly `0.0000`.

### D. Configuration References
- Milestone Script: `devops-configurations/scripts/test-local-milestone.ps1`
- Docker Compose: `devops-configurations/docker/docker-compose.yml`
- Database Schema: `src/main/resources/db/migration/`

### E. Verification & Validation Steps
Log in to the frontend application in your browser:
- Open `http://localhost`
- Enter user credentials or click demo login (if in dev profile)
- View dashboard balances, recent transactions, and loan portfolios
- Perform a simulated transfer between two test accounts
- Re-run the ledger balance query in Step 4 to confirm zero drift after transaction posting.

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `test-local-milestone.ps1` fails on Actuator Health**
   - *Root Cause:* Spring Boot container is still starting or failed database connection.
   - *Remediation:* Check logs with `docker compose logs fincore-backend`. If HikariCP timed out, check `docker compose logs fincore-db`.
2. **Failure Mode: Nginx returns 504 Gateway Timeout on API calls**
   - *Root Cause:* Backend is blocked by long-running database lock or deadlock.
   - *Remediation:* Check active PostgreSQL locks:
     ```powershell
     docker exec -it fincore-db psql -U fincore_user -d fincore_db -c "SELECT pid, query, wait_event_type, wait_event FROM pg_stat_activity WHERE wait_event_type IS NOT NULL;"
     ```
3. **Failure Mode: Net imbalance is NOT 0.00**
   - *Root Cause:* A transaction bypassed the double-entry accounting service or inserted an asymmetric ledger record.
   - *Remediation:* Inspect the latest records in `ledger_entries` by ordering by `created_at DESC LIMIT 10` to identify unbalanced transaction IDs.

### G. Rollback & Disaster Recovery Procedures
If local test data becomes corrupt during testing:
```powershell
# Clean teardown
docker compose down -v
# Re-launch pristine stack with automated Flyway initialization
docker compose --env-file devops-configurations/docker/.env.docker up -d --build
```

### H. Security & Compliance Guardrails
- Local test tokens expire in 15 minutes.
- Passwords are never returned in JSON API responses.
- Database ports on host are accessible only via loopback interface.

### I. Production vs Local Differences

| Dimension | Local Milestone 1 | Production Verification |
| :--- | :--- | :--- |
| **Testing Scope** | Single-user synthetic test transactions | Synthetic end-to-end canary transactions in live cluster |
| **Reconciliation Frequency** | Executed manually via script | Automated SRE cron job running every 60 seconds with PagerDuty alerts |
| **Telemetry** | Docker logs and curl | Distributed tracing in Tempo, Prometheus counters, Grafana dashboards |

### J. Interview Readiness Questions & Model Answers
- **Q1: How do you verify the financial correctness of a banking system prior to cloud deployment?**
  - *Answer:* Beyond unit and integration test suites, we verify financial data invariants directly at the database layer. Specifically, we execute ledger reconciliation queries that sum all historical debit and credit entries. The net difference must be exactly zero. If net imbalance is non-zero, it indicates single-sided posting or broken transaction atomicity, which blocks deployment immediately.
- **Q2: Why is a local milestone gate mandatory before provisioning cloud infrastructure?**
  - *Answer:* Cloud resources incur immediate monetary cost, security liability, and operational complexity. If an application contains startup race conditions, broken database migrations, or failing healthchecks, debugging them inside an AWS EKS cluster with NAT Gateways, IAM roles, and Load Balancers is exponentially more difficult and expensive than isolating them in a local Docker Compose network.
- **Q3: What metrics from Spring Boot Actuator provide early warning of database connection pool exhaustion?**
  - *Answer:* `hikaricp_connections_pending` (threads currently waiting for a connection), `hikaricp_connections_active` (connections currently in use), and `hikaricp_connections_acquire_seconds` (time taken to obtain a connection). If pending connections rise while active connections hit `maximumPoolSize`, transactions will start timing out with 500 errors.

### K. Cost & Performance Considerations
- Milestone 1 verification consumes zero cloud budget.
- Memory consumption across all 3 containers typically remains under 1.8 GB RAM.

### L. Real-World Banking Considerations
- Financial institutions are subject to SOX and Basel III regulatory audits. The automated reconciliation scripts developed in this milestone form the baseline for production end-of-day (EOD) financial closing audits.

### M. Progress Sign-off Checklist
- [ ] `test-local-milestone.ps1` completed with all checks PASSED.
- [ ] Authentication API generates valid JWT access token.
- [ ] Double-entry ledger audit query returns strictly 0.00 net imbalance.
- [ ] Milestone 1 signed off; ready for CI/CD and Cloud phases.

### N. Next Manual Transition
Proceed to **[Manual 07: Git Branching & Conventional Commits](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/07-git-branching-conventional-commits.md)** to establish GitFlow branching, pre-commit security hooks, and automated changelog generation.

