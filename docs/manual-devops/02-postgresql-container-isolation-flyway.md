# FinCore Banking Platform — DevOps Engineering Manual
## Manual 02: PostgreSQL Container Isolation & Flyway Migrations

### A. Objective & Architecture Context
FinCore relies on PostgreSQL 16+ for relational data persistence, ACID transactions, and strict double-entry ledger bookkeeping. This manual instructs how to provision an isolated, persistent PostgreSQL container (`fincore-db`) listening on host port `5435`, configure dedicated credentials, establish a named Docker volume to prevent data loss across restarts, and verify schema migrations from `V1` through `V7`.

### B. Prerequisites & Dependencies
- Manual 01 successfully completed (Docker engine active, port 5435 free).
- PostgreSQL client (`psql`) installed on host or available via `docker exec`.
- Maven and JDK 21 installed on host for running Flyway migration verification.

### C. Step-by-Step Implementation Instructions

#### Step 1: Create the Named Volume and Isolated Bridge Network
Execute in PowerShell:
```powershell
docker network create fincore-network
docker volume create fincore-db-data
```

#### Step 2: Launch the Isolated PostgreSQL Container
Run the container directly with explicit parameters matching `devops-configurations/docker/docker-compose.yml`:
```powershell
docker run -d `
  --name fincore-db `
  --network fincore-network `
  -p 5435:5432 `
  -e POSTGRES_DB=fincore_db `
  -e POSTGRES_USER=fincore_user `
  -e POSTGRES_PASSWORD=fincore_db_secret `
  -v fincore-db-data:/var/lib/postgresql/data `
  --health-cmd="pg_isready -U fincore_user -d fincore_db" `
  --health-interval=10s `
  --health-timeout=5s `
  --health-retries=5 `
  --restart unless-stopped `
  postgres:16-alpine
```

#### Step 3: Verify Container Health State
Monitor the startup until status reports `healthy`:
```powershell
docker inspect --format='{{json .State.Health.Status}}' fincore-db
```
Expected output: `"healthy"`

#### Step 4: Execute Flyway Migrations via Spring Boot Profile
The FinCore backend project includes Flyway migrations in `src/main/resources/db/migration/`:
- `V1__init_schema.sql` (Users, roles, audit logs)
- `V2__refresh_tokens.sql` (JWT refresh tokens)
- `V3__customer_accounts_ledger.sql` (Customers, accounts, ledger entries, transfer requests)
- `V4__system_reserve_initialization.sql` (System reserve account seeding)
- `V5__loans_schema.sql` (Loan management & repayment schedules)
- `V6__notifications_schema.sql` (Customer in-app notification center)
- `V7__fraud_detection_schema.sql` (Fraud rules, evaluations, and alerts)

To test migration against the container, run Maven Flyway info:
```powershell
mvn flyway:info `
  -Dflyway.url="jdbc:postgresql://localhost:5435/fincore_db" `
  -Dflyway.user="fincore_user" `
  -Dflyway.password="fincore_db_secret"
```

### D. Configuration References
- Docker Compose definition: `devops-configurations/docker/docker-compose.yml` (service `fincore-db`)
- Environment defaults: `devops-configurations/docker/.env.docker.example`
- SQL migrations: `src/main/resources/db/migration/V1__*.sql` through `V7__*.sql`

### E. Verification & Validation Steps
Connect directly into the running database container and query schema status:
```powershell
docker exec -it fincore-db psql -U fincore_user -d fincore_db -c "\dt"
```
Verify the ledger balance invariant (total debits equal total credits):
```powershell
docker exec -it fincore-db psql -U fincore_user -d fincore_db -c "
SELECT 
    COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END), 0) AS total_debits,
    COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END), 0) AS total_credits,
    COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE -amount END), 0) AS net_imbalance
FROM ledger_entries;"
```
Expected output: `net_imbalance = 0.00`

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `bind: address already in use` on port 5435**
   - *Root Cause:* A previous container or another service bound port 5435.
   - *Remediation:* Run `docker ps -a` to find older containers. Remove via `docker rm -f <container-id>`.
2. **Failure Mode: `FlywayException: Validate failed: Migrations have failed validation`**
   - *Root Cause:* Checksum mismatch because an applied migration file in `db/migration/` was modified after being recorded in `flyway_schema_history`.
   - *Remediation:* Never edit applied migration files. If in a clean local environment, drop the local container volume (`docker volume rm fincore-db-data`) and re-apply cleanly.
3. **Failure Mode: `FATAL: password authentication failed for user "fincore_user"`**
   - *Root Cause:* The named volume `fincore-db-data` was previously initialized with a different password. PostgreSQL does not update master passwords upon subsequent restarts if data files exist.
   - *Remediation:* Stop the container, remove the volume (`docker volume rm fincore-db-data`), and restart.

### G. Rollback & Disaster Recovery Procedures
To cleanly teardown the database without affecting other workstation services:
```powershell
docker stop fincore-db
docker rm fincore-db
# To reset database to pristine state (CAUTION: wipes container data):
docker volume rm fincore-db-data
```

### H. Security & Compliance Guardrails
- Container listens on `localhost:5435` only; do not expose to public interfaces (`0.0.0.0:5435`).
- Master credentials must be stored in `.env.docker` which is ignored by `.gitignore`.
- Database data directory permissions inside the container are strictly locked to `postgres` user (UID 70).

### I. Production vs Local Differences

| Dimension | Local Docker (`fincore-db`) | Production (AWS Aurora RDS) |
| :--- | :--- | :--- |
| **Instance Type** | Single-container PostgreSQL 16 Alpine | Multi-AZ Aurora PostgreSQL Serverless v2 |
| **Storage Engine** | Local Docker volume on host SSD | Distributed, 6-way replicated Aurora cluster volume |
| **Failover SLA** | Manual container restart (~10s downtime) | Automated failover in < 30 seconds with zero data loss |
| **Backup Strategy** | Local volume export / pg_dump | Continuous Point-In-Time-Recovery (PITR) to S3 |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why is named volume persistence critical for containerized database testing?**
  - *Answer:* Container lifecycles are ephemeral. If an anonymous volume is used, recreating the container causes total loss of applied Flyway migrations and seeded test data. Using a named volume (`fincore-db-data`) decouples data lifecycle from container lifecycle, mimicking enterprise block storage (EBS / SAN).
- **Q2: How do you prevent multi-replica Spring Boot services from competing on Flyway migrations in Kubernetes?**
  - *Answer:* In local development, Spring Boot runs Flyway on startup. In production with multiple pods, we disable startup migrations (`spring.flyway.enabled=false`) and run migrations inside an isolated Kubernetes `Job` or Helm `pre-upgrade` hook. This guarantees that DDL locks are held by a single process and schema evolution completes before application pods receive traffic.
- **Q3: What PostgreSQL parameters are essential for banking ledger ACID compliance?**
  - *Answer:* `synchronous_commit = on` (guarantees WAL write to disk before returning success), `wal_level = replica` or `logical` (for auditing and replication), and pessimistic row locking (`SELECT FOR UPDATE`) to prevent dirty reads and lost updates on customer account balances.

### K. Cost & Performance Considerations
- Memory limit for `fincore-db`: 1 GB RAM is sufficient for local development and testing thousands of transactions per second.
- Local disk I/O on NVMe SSD achieves sub-millisecond commit latency.

### L. Real-World Banking Considerations
- Regulatory compliance mandates immutable audit logs. In FinCore, Flyway migration history (`flyway_schema_history`) and financial ledger entries (`ledger_entries`) are strictly append-only. No UPDATE or DELETE statements are permitted on ledger records.

### M. Progress Sign-off Checklist
- [ ] Container `fincore-db` running and healthy.
- [ ] Port 5435 accepting connections.
- [ ] Flyway migrations V1 through V7 successfully applied.
- [ ] Ledger imbalance query returns 0.00 net drift.

### N. Next Manual Transition
Proceed to **[Manual 03: Backend Dockerfile Multi-Stage Optimization](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/03-backend-dockerfile-multistage-optimization.md)** to package the Spring Boot backend into a hardened, non-root OCI container image.

