# FinCore Banking Platform — DevOps Interview Mastery Guide

How to articulate the FinCore Platform in Senior and Staff DevOps/Platform Engineer technical interviews.

---

### 1. Elevator Pitch (2-Minute Architectural Overview)

> "FinCore Banking Platform is an enterprise-grade digital banking system engineered with a Java 21 / Spring Boot 3 modular monolith, PostgreSQL with strict double-entry ledger invariants, and a React/TypeScript frontend.
>
> My role was architecting and manually executing the end-to-end DevOps, DevSecOps, SRE, and cloud platform lifecycle:
> 1. Engineered multi-stage, hardened Docker containers with non-root security boundaries and layertools caching.
> 2. Implemented a 10-stage declarative CI/CD DevSecOps pipeline with automated gates for Gitleaks, SonarQube, OWASP SCA, Trivy SBOM, and ZAP DAST.
> 3. Provisioned a resilient AWS infrastructure using Terraform, including a multi-AZ VPC, EKS 1.30 managed cluster, Aurora PostgreSQL Serverless v2, and least-privilege IAM OIDC roles.
> 4. Deployed GitOps-driven delivery with Argo CD, Helm packaging, zero-trust Kubernetes NetworkPolicies, and AWS ALB Ingress.
> 5. Established full-stack SRE observability using Prometheus, Grafana, Promtail/Loki, and OpenTelemetry distributed tracing, mapping SLOs directly to banking transaction invariants.
> 6. Extended the platform with MLOps model tracking (MLflow) and an AIOps automated RCA engine correlating metric anomalies with distributed log traces."

---

### 2. High-Frequency Staff/Senior Interview Scenarios

#### Scenario 1: "How did you ensure database schema migrations didn't cause downtime or race conditions during Kubernetes rollouts?"
- **The Challenge:** When rolling out a new version of the backend with multiple replicas (e.g., 3 pods in an EKS cluster), allowing each pod to execute Flyway migrations on startup causes concurrency race conditions, lock contention on `flyway_schema_history`, or startup failures.
- **The Solution:** In FinCore, we decoupled schema migrations from application pod startup. For production rollouts, we execute Flyway via a dedicated Kubernetes `Job` or Helm `pre-upgrade` hook before the application pods roll out. Furthermore, we mandate non-breaking database schema changes (expand/contract pattern: add columns as nullable first, deploy code that writes to both, then backfill, rather than dropping or renaming columns synchronously).
- **Key Metric/Evidence:** Zero migration lock timeouts across rolling deployments; zero downtime during V1 to V7 database evolution.

#### Scenario 2: "How do you handle secrets securely across the CI/CD and Kubernetes lifecycle in a banking context?"
- **The Challenge:** Hardcoded secrets in Git repositories, plaintext environment variables in Kubernetes ConfigMaps, or long-lived static database credentials present severe security and PCI-DSS compliance risks.
- **The Solution:**
  - Shift-left: Gitleaks pre-commit hooks and Jenkins pipeline stages fail the build if high-entropy strings or private keys are committed.
  - In Kubernetes: We implemented HashiCorp Vault Agent Injector sidecars (and AWS Secrets Manager CSI driver). Pods receive short-lived, dynamically generated PostgreSQL credentials that automatically rotate every 60 minutes.
  - Zero plaintext secrets in GitOps manifests: Git contains only Vault injection annotations, never secret values.

#### Scenario 3: "Explain how you resolved a port collision or host network constraint on developer machines."
- **The Challenge:** Developers frequently run local PostgreSQL instances on default port `5432` for other services. Binding Docker Compose directly to `5432:5432` causes `bind: address already in use` failures.
- **The Solution:** In FinCore's Docker Compose architecture, we decoupled the host publishing port from the container's internal listening port. Inside the container bridge network, PostgreSQL listens on default `5432`. On the host, we publish to `5435:5432`. The backend application container connects via service discovery (`jdbc:postgresql://fincore-db:5432/fincore_db`), while external host tools connect to `localhost:5435`. This guarantees total isolation without stopping existing developer services.

---

### 3. Key System Invariants to Memorize for Interviews

1. **Double-Entry Balance Invariant:**
   $$\sum \text{Debit Entries} = \sum \text{Credit Entries} \iff \text{Net Imbalance} = 0.00$$
2. **Pessimistic Concurrency Invariant:**
   Account balances are updated exclusively via `SELECT ... FOR UPDATE` row locks within `@Transactional` boundaries, preventing race conditions and double-spending under concurrent transfers.
3. **Container Security Invariant:**
   Containers never run as UID 0 (`root`). Backend runs as `fincore` (UID 10001) using a read-only root filesystem wherever possible, with temporary directories mounted via `tmpfs`.
4. **GitOps Invariant:**
   The Git repository is the single source of truth. Direct `kubectl edit` or `kubectl apply` commands in production are strictly forbidden; Argo CD detects and reverts manual configuration drift automatically.

