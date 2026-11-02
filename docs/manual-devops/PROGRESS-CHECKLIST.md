# FinCore Banking Platform — Implementation Progress Checklist

Use this document to track your personal hands-on execution. Mark each item `[x]` only after running the verification commands and witnessing the expected outputs.

---

### Part 1: Local Docker & Database Foundations (Milestone 1)
- [ ] **Manual 01:** Docker Desktop started; WSL2 backend verified; port 5435 verified free.
- [ ] **Manual 02:** `fincore-db` container running on port 5435; named volume mounted; Flyway migrations V1–V7 applied.
- [ ] **Manual 03:** `backend.Dockerfile` built; layertools verified; non-root user `fincore` active.
- [ ] **Manual 04:** `frontend.Dockerfile` built; Nginx reverse proxy routing `/api/` to backend.
- [ ] **Manual 05:** `docker compose up -d` executed; all 3 containers healthy; bridge network operational.
- [ ] **Manual 06:** `test-local-milestone.ps1` passed; double-entry ledger balance audit returns 0.00 drift.

---

### Part 2: Version Control, CI/CD & DevSecOps Pipelines
- [ ] **Manual 07:** Git pre-commit hook installed; Gitleaks scan blocks accidental API key commit.
- [ ] **Manual 08:** Jenkins master/agent spun up; Declarative Jenkinsfile executes multi-stage build.
- [ ] **Manual 09:** SonarQube quality gate enforced; zero blocker bugs; test coverage > 80%.
- [ ] **Manual 10:** OWASP Dependency-Check Maven plugin scans third-party JARs; CVSS triage completed.
- [ ] **Manual 11:** Trivy scans backend/frontend container images; CycloneDX SBOM generated.
- [ ] **Manual 12:** OWASP ZAP baseline DAST scan executes against live local staging endpoints.

---

### Part 3: Terraform & AWS Cloud Infrastructure
- [ ] **Manual 13:** S3 bucket with SSE-AES256 and DynamoDB state lock table created via AWS CLI.
- [ ] **Manual 14:** Multi-AZ VPC, IGW, NAT Gateway, public/private/db subnets provisioned via Terraform.
- [ ] **Manual 15:** EKS 1.30 managed cluster and managed node groups provisioned; `kubectl get nodes` returns Ready.
- [ ] **Manual 16:** Amazon RDS Aurora PostgreSQL cluster provisioned; secrets stored in AWS Secrets Manager.
- [ ] **Manual 17:** Terraform state drift detection script tested; remote state locking verified.

---

### Part 4: Kubernetes, Helm & GitOps Delivery
- [ ] **Manual 18:** `fincore` namespace, ConfigMaps, and Secrets deployed; backend and frontend pods running.
- [ ] **Manual 19:** Zero-trust NetworkPolicy applied; cross-namespace isolation and pod egress restricted.
- [ ] **Manual 20:** AWS Load Balancer Controller installed; ALB Ingress serves traffic with TLS certificate.
- [ ] **Manual 21:** FinCore Helm chart created, packaged, and linted; dev/prod values validated.
- [ ] **Manual 22:** Argo CD installed; FinCore Application CRD synced; GitOps self-healing tested.

---

### Part 5: Observability, Logging, Tracing & SRE
- [ ] **Manual 23:** Prometheus scraping Spring Boot Actuator `/actuator/prometheus`; alert rules loaded.
- [ ] **Manual 24:** Grafana dashboard imported; JVM heap, transaction throughput, and HTTP p99 visible.
- [ ] **Manual 25:** Promtail scraping pod logs; Loki aggregating logs; LogQL forensic queries working.
- [ ] **Manual 26:** OpenTelemetry Collector receiving OTLP spans; Tempo visualizes distributed transfer trace.
- [ ] **Manual 27:** SRE SLIs/SLOs documented; Error Budget burn alert simulated; Sev-1 runbook rehearsed.

---

### Part 6: Secrets Management, MLOps, AIOps, FinOps & Portfolio
- [ ] **Manual 28:** HashiCorp Vault Agent Injector provisions dynamic database credentials to backend pod.
- [ ] **Manual 29:** MLflow tracking server records synthetic fraud detection rule baseline and feature store specs.
- [ ] **Manual 30:** AIOps anomaly detector pipeline simulates transfer surge and correlates root cause in Loki.
- [ ] **Manual 31:** AWS Cost Explorer tags activated; single NAT Gateway toggle applied; cost audit report written.
- [ ] **Manual 32:** GitHub repository README polished with architecture diagrams; interview scenario scripts practiced.

