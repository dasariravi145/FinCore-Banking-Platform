# FinCore Banking Platform — Known Issues & Architectural Boundaries

**Document Version:** 1.0.0 (Phase 05/06 Final Audit)  
**Classification:** Engineering Internal / DevOps Reference  
**Last Updated:** November 2026  

---

## 1. Executive Summary

This document captures the comprehensive inventory of known limitations, architectural trade-offs, and environmental constraints identified during the **Prompt 05/06 Full-Stack Integration Audit & Verification**. These items do not represent architectural defects; rather, they reflect documented security boundaries, operational prerequisites, and deliberate banking design decisions.

---

## 2. Issues & Architectural Boundaries Inventory

### Issue 1: Real PostgreSQL Local Integration Blocked on Host
- **Severity:** P2 (Local Environmental Limitation)
- **Status:** **BLOCKED (Documented Exception)**
- **Root Cause:**
  - The local Windows host is running `postgresql-x64-18` on TCP port 5432.
  - However, the required application database user (`fincore_user`) and database (`fincore_db`) have not been provisioned on the host.
  - Attempting to connect as superuser `postgres` fails password authentication because the installation password is unknown to the agent and outside the workspace scope.
  - Docker Desktop is installed on the host but the daemon is inactive.
- **Impact:**
  - Automated integration testing and build verification execute against the isolated H2 database profile (`application-test.yml`), which successfully runs all 84 unit, integration, accounting correctness, and security tests.
- **DevOps Remediation (Prompt 06):**
  - In Prompt 06, an isolated PostgreSQL 16/18 container will be provisioned via Docker Compose or Kubernetes manifests with dedicated `fincore_user` credentials and pre-configured volume mounts, completely decoupling the platform from host PostgreSQL configurations.

---

### Issue 2: In-Memory JWT Access Token Purged on Browser Page Reload
- **Severity:** P3 (Deliberate Security Design Choice)
- **Status:** **BY DESIGN**
- **Details:**
  - In compliance with banking application security best practices, JWT access tokens are held exclusively in React runtime memory (`AuthContext`).
  - No access tokens or refresh tokens are ever persisted to `localStorage` or `sessionStorage`, completely neutralizing persistent Cross-Site Scripting (XSS) credential theft.
- **Impact:**
  - Hard-refreshing (`F5` / `Ctrl+R`) or closing the browser tab purges the token from client memory, returning the user to the `/login` screen.
- **Development Mitigation:**
  - In local development (`import.meta.env.DEV === true`), preset demo credential buttons (`Preset Admin`, `Preset Customer`) are rendered on `/login` for rapid re-authentication during manual verification.
  - In production builds (`npm run build`), these demo presets are automatically omitted from the bundle.

---

### Issue 3: Pre-Transfer Fraud Assessment Review Does Not Auto-Execute Transfers
- **Severity:** P3 (Banking Regulatory Compliance Protocol)
- **Status:** **VERIFIED BANKING PROTOCOL**
- **Details:**
  - When an impending transfer triggers a risk score $\ge 70$, it is blocked with HTTP 422 (`FraudBlockedException`), leaving customer account balances untouched.
  - The flagged assessment is recorded in `fraud_assessments` and made visible on the Admin Compliance Dashboard (`/admin/fraud`).
  - When a compliance officer reviews and marks the assessment as `APPROVED` with review notes, the system updates the assessment record in the compliance ledger.
  - It **deliberately does NOT automatically execute or replay the blocked transfer**.
- **Regulatory Rationale:**
  - Automated post-adjudication funds movement without an active, explicit customer instruction violates banking audit protocols. The customer must submit a fresh, authenticated transfer request after compliance clearance.

---

### Issue 4: Testcontainers Integration Test Skipped in Headless Environments
- **Severity:** P3 (Test Suite Design Choice)
- **Status:** **SKIPPED GRACEFULLY**
- **Details:**
  - `PostgresTestcontainersIntegrationTest.java` requires an active Docker daemon to spin up a PostgreSQL container.
  - In environments where Docker Desktop is not started or daemon socket is unreachable, the test detects the absence of a Docker client and skips gracefully (1 test skipped, 84 passed).
- **Remediation for CI/CD:**
  - In Jenkins/GitHub Actions pipelines with Docker-in-Docker (DinD), Testcontainers will automatically execute.

---

### Issue 5: Spring Data Web PageImpl Serialization Warning
- **Severity:** P4 (Non-Breaking Informational Warning)
- **Status:** **INFORMATIONAL**
- **Details:**
  - Spring Boot 3.3 emits a warning log: `Serializing PageImpl instances as-is is not supported... For a stable JSON structure, please use Spring Data's PagedModel`.
- **Impact:**
  - Client-side consumers (React Query and Axios) successfully receive and parse the `content`, `totalPages`, `totalElements`, and `number` fields without any regression.

