# FinCore Banking Platform — DevOps Engineering Manual
## Manual 03: Backend Dockerfile Multi-Stage Optimization & Container Hardening

### A. Objective & Architecture Context
FinCore's backend is a Spring Boot 3.4.1 modular monolith compiled on Java 21. Standard monolithic fat-JAR container images carry multi-gigabyte build toolchains (Maven, full JDK) and suffer slow CI/CD layer uploads. This manual explains the multi-stage build, Spring Boot layertools extraction, non-root security isolation, and JVM container-aware memory tuning implemented in `devops-configurations/docker/backend.Dockerfile`.

### B. Prerequisites & Dependencies
- Docker Desktop running (from Manual 01).
- JDK 21 and Maven on host for local pre-compilation (optional, as Dockerfile builds inside container).
- FinCore backend source files present in root repository.

### C. Step-by-Step Implementation Instructions

#### Step 1: Inspect the Multi-Stage Architecture
The Dockerfile uses 3 distinct stages:
1. **Builder Stage (`maven:3.9.9-eclipse-temurin-21-alpine`):** Compiles source code, downloads Maven dependencies, and packages the executable JAR.
2. **Extractor Stage (`eclipse-temurin:21-jre-alpine`):** Executes Spring Boot layertools mode to split the fat-JAR into 4 distinct cacheable layers:
   - `dependencies/` (Third-party libraries, rarely changes)
   - `spring-boot-loader/` (Spring Boot launcher classes)
   - `snapshot-dependencies/` (Internal shared snapshot libraries)
   - `application/` (FinCore business logic and controllers, changes every commit)
3. **Runtime Stage (`eclipse-temurin:21-jre-alpine`):** Copies only extracted layers, creates a non-root system user, configures JVM ergonomics, and defines container health probes.

#### Step 2: Build the Backend Image Locally
Open PowerShell in the project root directory (`D:\Projects\FinCore-Banking-Platform`) and execute:
```powershell
docker build `
  -f devops-configurations/docker/backend.Dockerfile `
  -t fincore-backend:1.0.0 `
  -t fincore-backend:latest .
```

#### Step 3: Inspect Image Size and Security Metadata
Verify that build tools were excluded and inspect final image dimensions:
```powershell
docker images fincore-backend
```
*Expected Size:* Under 280 MB (compared to 900+ MB for an unoptimized JDK image).

#### Step 4: Verify Non-Root User Execution
Inspect the container's configured user identity:
```powershell
docker run --rm fincore-backend:latest id
```
*Expected Output:* `uid=10001(fincore) gid=10001(fincore) groups=10001(fincore)`

### D. Configuration References
- Dockerfile: `devops-configurations/docker/backend.Dockerfile`
- Context ignore rules: `devops-configurations/docker/.dockerignore` (excludes `.git`, `target`, `node_modules`, IDE configs)
- JVM Container Flags:
  ```properties
  -XX:+UseContainerSupport
  -XX:MaxRAMPercentage=75.0
  -XX:InitialRAMPercentage=50.0
  -XX:+ExitOnOutOfMemoryError
  ```

### E. Verification & Validation Steps
Test running the standalone backend container connected to `fincore-network`:
```powershell
docker run -d `
  --name fincore-backend-test `
  --network fincore-network `
  -p 8080:8080 `
  -e SPRING_PROFILES_ACTIVE=dev `
  -e DB_URL=jdbc:postgresql://fincore-db:5432/fincore_db `
  -e DB_USER=fincore_user `
  -e DB_PASSWORD=fincore_db_secret `
  fincore-backend:latest
```
Check health endpoint via curl:
```powershell
curl -s http://localhost:8080/actuator/health
```
Expected response:
```json
{"status":"UP"}
```
Clean up test container:
```powershell
docker stop fincore-backend-test && docker rm fincore-backend-test
```

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `java.lang.OutOfMemoryError: Java heap space` or container killed (OOMKilled - Exit code 137)**
   - *Root Cause:* Container memory limit was set below JVM heap requirements.
   - *Remediation:* Ensure container is allocated at least 768 MB RAM. In Docker Compose, set `deploy.resources.limits.memory: 1024M`.
2. **Failure Mode: `Permission denied: /app/...`**
   - *Root Cause:* Files were copied as `root` without `--chown=fincore:fincore`.
   - *Remediation:* Verify that all `COPY` commands in the runtime stage specify `--chown=fincore:fincore`.
3. **Failure Mode: `org.postgresql.util.PSQLException: Connection to fincore-db:5432 refused`**
   - *Root Cause:* Container was started without attaching to `fincore-network` where `fincore-db` resides.
   - *Remediation:* Always include `--network fincore-network`.

### G. Rollback & Disaster Recovery Procedures
If a new backend image introduces a critical regression:
```powershell
docker tag fincore-backend:1.0.0 fincore-backend:latest
docker run -d --name fincore-backend --network fincore-network -p 8080:8080 fincore-backend:1.0.0
```

### H. Security & Compliance Guardrails
- **Zero Root Execution:** Never run as UID 0 in production. Running as non-root mitigates container breakout vulnerabilities.
- **Minimal Attack Surface:** Alpine base image contains no package managers (`curl`, `apk` removed or uninstalled in runtime).
- **Graceful Shutdown:** Spring Boot property `server.shutdown=graceful` allows pending HTTP requests to complete within 30s before SIGKILL.

### I. Production vs Local Differences

| Dimension | Local Docker Build | Production (CI/CD & ECR) |
| :--- | :--- | :--- |
| **Image Registry** | Local Docker daemon cache | AWS Elastic Container Registry (ECR) with KMS encryption |
| **Vulnerability Scanning** | Manual `trivy image` scan | Automated Trivy & AWS Inspector scans blocking deployment |
| **Base Image Mirror** | Public Docker Hub (`eclipse-temurin`) | Internal hardened golden base image repository |

### J. Interview Readiness Questions & Model Answers
- **Q1: What is the architectural purpose of Spring Boot layertools in Docker builds?**
  - *Answer:* In a standard fat-JAR, any one-line code change requires re-uploading the entire JAR (100+ MB) as a single layer. Layertools separates static dependencies (Spring, Hibernate, Jackson) into separate layers. When developer code changes, Docker only rebuilds and pushes the tiny `application/` layer (often < 2 MB), cutting CI/CD build and push times by up to 90%.
- **Q2: Why are `-XX:+ExitOnOutOfMemoryError` and `-XX:MaxRAMPercentage` critical in containerized JVMs?**
  - *Answer:* Older JVMs did not recognize cgroup container memory limits and defaulted to 25% of physical host RAM, causing sudden OOMKills. `-XX:MaxRAMPercentage=75.0` dynamically calculates heap size based on the container's cgroup limit. `-XX:+ExitOnOutOfMemoryError` forces the JVM process to terminate immediately upon OOM, allowing Kubernetes or Docker to restart the pod clean rather than lingering in a hung, unresponsive state.
- **Q3: How do you verify non-root compliance in automated CI/CD pipelines?**
  - *Answer:* We use linters like Hadolint to enforce the presence of a `USER` instruction, combined with Trivy config scans and runtime admission controllers (such as Kyverno or Pod Security Standards set to `Restricted`) that reject pods running as UID 0.

### K. Cost & Performance Considerations
- Multi-stage layertools reduces image transfer bandwidth over AWS NAT Gateways and ECR endpoints, directly reducing data transfer costs in high-frequency CI/CD pipelines.

### L. Real-World Banking Considerations
- Financial regulators require immutable auditability for software artifacts. Every image digest (SHA256) is recorded and signed with Cosign before promotion to staging or production.

### M. Progress Sign-off Checklist
- [ ] `backend.Dockerfile` builds successfully without warnings.
- [ ] Non-root UID 10001 confirmed.
- [ ] Image size verified under 300 MB.
- [ ] Healthcheck endpoint `/actuator/health` returns status UP.

### N. Next Manual Transition
Proceed to **[Manual 04: Frontend Dockerfile Nginx Reverse Proxy](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/04-frontend-dockerfile-nginx-reverse-proxy.md)** to build the React/TypeScript portal with production Nginx routing.

