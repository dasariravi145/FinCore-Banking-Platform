# FinCore Banking Platform — DevOps Engineering Manual
## Manual 05: Docker Compose Full-Stack Orchestration & Service Discovery

### A. Objective & Architecture Context
Running multiple standalone containers via manual `docker run` commands is error-prone and lacks dependency management. In FinCore, the backend requires a healthy PostgreSQL database with completed Flyway migrations before opening port 8080, and the frontend reverse proxy requires an active backend. This manual instructs how to manage the full banking topology with Docker Compose using `devops-configurations/docker/docker-compose.yml`.

### B. Prerequisites & Dependencies
- Manuals 01–04 completed.
- Docker Compose v2.20+ installed.
- Host port 5435 verified free (local Windows PostgreSQL 18 left untouched on 5432).

### C. Step-by-Step Implementation Instructions

#### Step 1: Prepare Environment File
Navigate to `devops-configurations/docker/` and create `.env.docker` from the provided example:
```powershell
cd D:\Projects\FinCore-Banking-Platform\devops-configurations\docker
Copy-Item .env.docker.example .env.docker
```
Verify the contents of `.env.docker`:
```properties
POSTGRES_DB=fincore_db
POSTGRES_USER=fincore_user
POSTGRES_PASSWORD=fincore_db_secret
POSTGRES_HOST_PORT=5435
BACKEND_PORT=8080
FRONTEND_PORT=80
FRONTEND_DEV_PORT=5173
JWT_SECRET=fincore-banking-super-secret-jwt-key-2026-production-ready-32bytes!
SPRING_PROFILES_ACTIVE=dev
```

#### Step 2: Review the Docker Compose Topology
Inspect `devops-configurations/docker/docker-compose.yml`:
- **`fincore-db`:**
  - Image: `postgres:16-alpine`
  - Published Port: `${POSTGRES_HOST_PORT:-5435}:5432`
  - Healthcheck: `pg_isready -U fincore_user -d fincore_db` every 5s
  - Volume: `fincore-db-data:/var/lib/postgresql/data`
- **`fincore-backend`:**
  - Build context: Repository root, Dockerfile: `devops-configurations/docker/backend.Dockerfile`
  - Published Port: `${BACKEND_PORT:-8080}:8080`
  - Dependency: `depends_on: fincore-db: condition: service_healthy`
  - Internal DB URL: `jdbc:postgresql://fincore-db:5432/fincore_db`
- **`fincore-frontend`:**
  - Build context: Repository root, Dockerfile: `devops-configurations/docker/frontend.Dockerfile`
  - Published Ports: `80:80` and `5173:80`
  - Dependency: `depends_on: fincore-backend: condition: service_healthy`

#### Step 3: Launch the Full Stack
Execute:
```powershell
docker compose --env-file .env.docker up -d --build
```

#### Step 4: Monitor Orchestrated Startup Logs
Stream logs to verify startup order:
```powershell
docker compose logs -f
```
Verify that `fincore-db` transitions to healthy, followed by Spring Boot initialization and Flyway migrations, followed by Nginx worker startup.

### D. Configuration References
- Compose Manifest: `devops-configurations/docker/docker-compose.yml`
- Environment Template: `devops-configurations/docker/.env.docker.example`
- Local Script: `devops-configurations/scripts/test-local-milestone.ps1`

### E. Verification & Validation Steps
Check the running status of all services:
```powershell
docker compose ps
```
Expected output:
```
NAME               IMAGE                     COMMAND                  SERVICE            STATUS
fincore-db         postgres:16-alpine        "docker-entrypoint.s…"   fincore-db         Up (healthy)
fincore-backend    fincore-docker-backend    "java org.springfram…"   fincore-backend    Up (healthy)
fincore-frontend   fincore-docker-frontend   "/docker-entrypoint.…"   fincore-frontend   Up (healthy)
```

Verify backend health via port 8080:
```powershell
curl http://localhost:8080/actuator/health
```

Verify frontend portal via port 80:
```powershell
curl -I http://localhost
```

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `fincore-backend` in `CrashLoopBackOff` waiting for database**
   - *Root Cause:* Backend started before PostgreSQL was ready to accept socket connections.
   - *Remediation:* Verify that `docker-compose.yml` uses `depends_on` with `condition: service_healthy` rather than simple string dependencies.
2. **Failure Mode: `connection refused` between backend and database**
   - *Root Cause:* Backend attempted to connect to `localhost:5432` instead of Docker container DNS `fincore-db:5432`.
   - *Remediation:* Ensure `SPRING_DATASOURCE_URL=jdbc:postgresql://fincore-db:5432/fincore_db` is configured in compose environment block.
3. **Failure Mode: Port 5435 conflict on host**
   - *Root Cause:* A previously orphaned `fincore-db` container from Manual 02 is running.
   - *Remediation:* Run `docker rm -f fincore-db` before launching `docker compose up`.

### G. Rollback & Disaster Recovery Procedures
To stop all services and preserve database data:
```powershell
docker compose down
```
To stop all services and completely reset volume data (CAUTION: deletes container database):
```powershell
docker compose down -v
```

### H. Security & Compliance Guardrails
- Services communicate over an internal isolated bridge network (`fincore-network`). Database port 5432 is not exposed to the public internet.
- Secrets are sourced from local `.env.docker` which is excluded from Git.
- Resource limits enforce memory caps (Backend: 1 GB, DB: 1 GB, Frontend: 128 MB) to prevent denial-of-service on the host.

### I. Production vs Local Differences

| Dimension | Local Docker Compose | Production (Kubernetes / EKS) |
| :--- | :--- | :--- |
| **Orchestration** | Single-node Docker daemon | Multi-node Kubernetes 1.30 cluster |
| **Service Discovery** | Embedded Docker DNS (`fincore-db`) | CoreDNS + Kubernetes ClusterIP Services |
| **High Availability** | Single container per service | Multi-replica Deployments with Pod Anti-Affinity |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why is `condition: service_healthy` essential when orchestrating databases with Docker Compose?**
  - *Answer:* Standard `depends_on` only verifies that the database container process has started (PID assigned), not that the database engine is ready to accept TCP connections. PostgreSQL requires several seconds to initialize WAL logs and shared memory. Without `service_healthy`, the backend starts immediately, fails on connection refused, and crashes before Flyway can run.
- **Q2: How does Docker Compose handle DNS resolution between containers?**
  - *Answer:* Docker spins up an embedded DNS server at `127.0.0.11`. When containers join a custom user-defined bridge network, the DNS server resolves service names (e.g. `fincore-backend`, `fincore-db`) directly to container IP addresses, enabling zero-configuration service discovery.
- **Q3: What are the trade-offs of Docker Compose versus local Minikube/Kind for developer environments?**
  - *Answer:* Docker Compose is lightweight, fast to spin up (< 30 seconds), consumes minimal CPU/RAM, and requires no Kubernetes manifest overhead, making it ideal for inner-loop feature development. Minikube/Kind provides high-fidelity Kubernetes emulation (RBAC, Ingress, NetworkPolicies) but demands substantially higher host resources and slower startup times.

### K. Cost & Performance Considerations
- CPU limit of 1.0 core and memory limit of 1024M for Spring Boot provides optimal throughput without starving host developer tools.

### L. Real-World Banking Considerations
- In banking environments, local compose environments must mirror production logging and database isolation rules so that security vulnerabilities or concurrency bugs are caught before promotion to cloud environments.

### M. Progress Sign-off Checklist
- [ ] `.env.docker` created and populated.
- [ ] `docker compose up -d` brings up all 3 containers.
- [ ] All 3 containers report `healthy`.
- [ ] Frontend accessible at `http://localhost`.

### N. Next Manual Transition
Proceed to **[Manual 06: Local Milestone Verification & Troubleshooting](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/06-local-milestone-verification-troubleshooting.md)** to run automated health verification and perform end-to-end banking reconciliation audits.

