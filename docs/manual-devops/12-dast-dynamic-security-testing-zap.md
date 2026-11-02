# FinCore Banking Platform — DevOps Engineering Manual
## Manual 12: Dynamic Application Security Testing (DAST) with OWASP ZAP

### A. Objective & Architecture Context
Static analysis (SAST) and software composition analysis (SCA) examine inactive code and libraries at rest. Dynamic Application Security Testing (DAST) analyzes the live, running application from an external attacker's perspective (black-box testing). In FinCore, DAST validates that HTTP security response headers are active, CORS headers prevent unauthorized cross-origin requests, JWT session tokens are transmitted securely, and API endpoints do not leak stack traces or sensitive internal paths under attack payloads.

### B. Prerequisites & Dependencies
- Local Docker Compose stack running and healthy (Manual 05).
- Docker engine active to run official OWASP ZAP container (`zaproxy/zap-stable`).
- FinCore endpoints reachable on `http://localhost:8080` (Backend) and `http://localhost` (Frontend).

### C. Step-by-Step Implementation Instructions

#### Step 1: Review OWASP ZAP Baseline Configuration
Inspect `devops-configurations/security/zap-baseline.conf`:
```properties
# OWASP ZAP Baseline Scan Rule Configuration for FinCore Banking
# WARN or FAIL on security regressions

10020   FAIL    (X-Frame-Options Header Missing)
10021   FAIL    (X-Content-Type-Options Header Missing)
10038   FAIL    (Content-Security-Policy Header Missing)
10055   WARN    (CORS Misconfiguration)
10054   FAIL    (Cookie Without SameSite Attribute)
10011   FAIL    (Server Leaks Version Information via "Server" Header)
10023   WARN    (Information Disclosure - Debug Error Messages)
```

#### Step 2: Execute ZAP Baseline Scan Against Running Frontend Portal
Run the OWASP ZAP baseline scan via Docker against the Nginx frontend reverse proxy:
```powershell
docker run --rm `
  --network host `
  -v ${PWD}/zap-reports:/zap/wrk/:rw `
  zaproxy/zap-stable zap-baseline.py `
  -t http://localhost `
  -c devops-configurations/security/zap-baseline.conf `
  -r zap-frontend-report.html `
  -I
```
*Note on Windows:* If `--network host` is unsupported in your Docker Desktop configuration, use `host.docker.internal` as the target URL:
```powershell
docker run --rm `
  -v ${PWD}/zap-reports:/zap/wrk/:rw `
  zaproxy/zap-stable zap-baseline.py `
  -t http://host.docker.internal `
  -c devops-configurations/security/zap-baseline.conf `
  -r zap-frontend-report.html `
  -I
```

#### Step 3: Execute OpenAPI / Swagger API Security Scan Against Backend
ZAP can parse OpenAPI specifications to crawl and fuzz authenticated backend REST endpoints:
```powershell
docker run --rm `
  -v ${PWD}/zap-reports:/zap/wrk/:rw `
  zaproxy/zap-stable zap-api-scan.py `
  -t http://host.docker.internal:8080/v3/api-docs `
  -f openapi `
  -r zap-api-report.html `
  -I
```

#### Step 4: Triage ZAP Findings in Generated HTML Reports
Open `zap-reports/zap-frontend-report.html` and `zap-reports/zap-api-report.html` in your browser.
Review the categorized alert levels:
- **Red (High):** Direct vulnerabilities requiring immediate fix.
- **Orange (Medium):** Header or configuration weaknesses.
- **Yellow (Low):** Informational security hardening opportunities.
- **Blue (Informational):** Fingerprinting details.

### D. Configuration References
- ZAP configuration: `devops-configurations/security/zap-baseline.conf`
- Nginx security headers: `devops-configurations/nginx/nginx.conf`
- Spring Security configuration: `SecurityConfig.java`

### E. Verification & Validation Steps
Verify that the ZAP scan completes and confirms:
- 0 High Severity alerts.
- Nginx correctly sets `X-Frame-Options: DENY` and `X-Content-Type-Options: nosniff`.
- Spring Boot returns standardized error JSON without exposing Java stack traces or database schema table names when fed malformed JSON payloads.

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: ZAP fails to connect to target URL (`Connection refused`)**
   - *Root Cause:* Container networking boundary prevented the ZAP container from reaching `localhost` on the Windows host.
   - *Remediation:* Target `http://host.docker.internal:8080` instead of `http://localhost:8080`.
2. **Failure Mode: ZAP fails rule 10038 (`Content-Security-Policy Header Missing`) on backend**
   - *Root Cause:* Spring Boot REST APIs do not serve HTML directly and typically omit CSP unless configured.
   - *Remediation:* Ensure CSP headers are configured in Spring Security for HTML-serving endpoints or downgrade rule 10038 to `IGNORE` for raw JSON API endpoints in `zap-baseline.conf`.
3. **Failure Mode: False positive alert on cookie SameSite attribute**
   - *Root Cause:* In local development without HTTPS, cookies cannot have `Secure` and `SameSite=None` attributes.
   - *Remediation:* Confirm that in local dev profiles, cookies use `SameSite=Lax`.

### G. Rollback & Disaster Recovery Procedures
If a DAST scan discovers a high-severity remote injection or data leak in production:
1. Block the affected API route at the AWS WAF or Ingress level immediately.
2. Trigger the automated rollback to the previous certified container image via Argo CD.
3. Re-run the ZAP scan against the rolled-back target to confirm threat mitigation.

### H. Security & Compliance Guardrails
- DAST scans must run against dedicated staging/sandbox environments, NEVER against live production financial databases with destructive fuzzing payloads.
- Automated API fuzzing accounts must be restricted from initiating external SWIFT/ACH payment transfers.

### I. Production vs Local Differences

| Dimension | Local Staging Scan | Production Penetration Testing |
| :--- | :--- | :--- |
| **Tool** | OWASP ZAP Baseline Docker scan | Continuous external attack surface management & annual CREST-certified red-team pentest |
| **Payload Scope** | Passive crawl and non-destructive fuzzing | Deep authenticated vulnerability fuzzing and business logic exploitation testing |
| **Traffic Path** | Direct localhost loopback | Ingress through AWS CloudFront, AWS WAF, and ALB |

### J. Interview Readiness Questions & Model Answers
- **Q1: What is the difference between DAST and SAST, and why are both mandatory in DevSecOps?**
  - *Answer:* SAST analyzes source code without executing it; it excels at identifying coding flaws (such as SQL injection patterns or hardcoded secrets) early in the developer workflow. DAST tests the fully compiled, running application from an external attacker's vantage point; it excels at detecting runtime environment issues, such as missing HTTP security headers, TLS misconfigurations, CORS bypasses, and authentication session flaws that cannot be detected by analyzing source code alone.
- **Q2: Why should DAST API scans utilize an OpenAPI / Swagger definition?**
  - *Answer:* Traditional web crawlers rely on following HTML anchor tags (`<a href>`), making them ineffective at discovering modern headless REST APIs that respond only to structured JSON payloads over `POST`, `PUT`, or `DELETE`. Providing an OpenAPI specification allows ZAP to understand exact endpoint paths, expected parameter schemas, and payload types, ensuring 100% API attack surface coverage.
- **Q3: How do you prevent DAST automated scans from corrupting database test state?**
  - *Answer:* We execute DAST scans in an isolated, ephemeral staging environment seeded with synthetic test data. The database is restored from a clean snapshot immediately following the scan. Furthermore, destructive endpoints (e.g. `DELETE /api/v1/accounts`) can be excluded from aggressive fuzzing profiles via ZAP regex exclusions.

### K. Cost & Performance Considerations
- OWASP ZAP is free and open-source ($0 licensing cost).
- Running baseline scans takes under 3 minutes, making it suitable for automated staging deployment pipelines.

### L. Real-World Banking Considerations
- Financial regulatory standards (PCI-DSS 4.0 Requirement 11.3) mandate quarterly external vulnerability scans and annual penetration testing. Embedding automated OWASP ZAP scans into the CI/CD pipeline guarantees continuous compliance rather than scrambling before annual audits.

### M. Progress Sign-off Checklist
- [ ] `zap-baseline.conf` rules configured.
- [ ] ZAP baseline scan executed against frontend.
- [ ] ZAP API scan executed against Spring Boot OpenAPI endpoint.
- [ ] 0 High Severity alerts confirmed.

### N. Next Manual Transition
Proceed to **[Manual 13: AWS Account Setup, IAM Least Privilege & S3 Remote State Backend](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/13-aws-account-iam-s3-backend-setup.md)** to begin Part 3 (Cloud Infrastructure as Code with Terraform).

