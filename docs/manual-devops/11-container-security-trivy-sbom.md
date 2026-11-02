# FinCore Banking Platform — DevOps Engineering Manual
## Manual 11: Container Security Scanning with Trivy & SBOM Generation

### A. Objective & Architecture Context
Even when application source code and third-party JARs pass security analysis, the underlying container base images (Alpine Linux, OpenSSL, musl libc, Nginx binaries) may introduce critical vulnerabilities. This manual establishes container image vulnerability scanning and automated Software Bill of Materials (SBOM) generation using Aqua Security Trivy, enforcing gate policies before container images are pushed to AWS ECR.

### B. Prerequisites & Dependencies
- Docker images `fincore-backend:latest` and `fincore-frontend:latest` built locally (Manuals 03 & 04).
- Trivy CLI installed locally (`brew install trivy` or `winget install aqua.trivy`) or executed via official Docker container (`aquasec/trivy:latest`).

### C. Step-by-Step Implementation Instructions

#### Step 1: Inspect the Automated Trivy Scan Script
Review `devops-configurations/security/trivy-scan.sh`:
```bash
#!/usr/bin/env bash
# FinCore Banking Platform — Container Vulnerability & SBOM Scanner
set -euo pipefail

IMAGE_NAME="${1:-fincore-backend:latest}"
SEVERITY_LEVEL="CRITICAL,HIGH"

echo "=========================================================="
echo "Scanning Image: ${IMAGE_NAME} for ${SEVERITY_LEVEL} vulnerabilities"
echo "=========================================================="

# 1. Vulnerability Scan (Fails on fixable High/Critical CVEs)
trivy image \
  --severity "${SEVERITY_LEVEL}" \
  --ignore-unfixed \
  --exit-code 1 \
  "${IMAGE_NAME}"

# 2. Generate CycloneDX SBOM
echo "Generating CycloneDX Software Bill of Materials (SBOM)..."
trivy image \
  --format cyclonedx \
  --output "${IMAGE_NAME##*/}-cyclonedx-sbom.json" \
  "${IMAGE_NAME}"

echo "Container scan passed! SBOM generated successfully."
```

#### Step 2: Execute Trivy Scan against Backend Image
Run the scan using Docker on PowerShell:
```powershell
docker run --rm `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -v ${PWD}/reports:/reports `
  aquasec/trivy:latest image `
  --severity HIGH,CRITICAL `
  --ignore-unfixed `
  --exit-code 1 `
  --format table `
  fincore-backend:latest
```
*Expected Result:* Zero fixable High or Critical vulnerabilities detected; exit code 0.

#### Step 3: Execute Trivy Scan against Frontend Image
```powershell
docker run --rm `
  -v /var/run/docker.sock:/var/run/docker.sock `
  aquasec/trivy:latest image `
  --severity HIGH,CRITICAL `
  --ignore-unfixed `
  --exit-code 1 `
  --format table `
  fincore-frontend:latest
```

#### Step 4: Generate CycloneDX SBOM for Banking Audits
Generate an industry-standard CycloneDX JSON SBOM:
```powershell
docker run --rm `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -v ${PWD}:/output `
  aquasec/trivy:latest image `
  --format cyclonedx `
  --output /output/fincore-backend-sbom.json `
  fincore-backend:latest
```
Verify the generated `fincore-backend-sbom.json` contains metadata, components, and cryptographic hashes for all packages.

### D. Configuration References
- Trivy Bash Script: `devops-configurations/security/trivy-scan.sh`
- Dockerfiles: `devops-configurations/docker/backend.Dockerfile`, `frontend.Dockerfile`
- Jenkins Pipeline: `devops-configurations/jenkins/Jenkinsfile` (Stage 8)

### E. Verification & Validation Steps
Inspect the scan output summary:
- Total OS Packages Scanned (Alpine).
- Total Application Dependencies Scanned (Spring Boot, Jackson, Hibernate).
- Vulnerability count: 0 Critical, 0 High.
- SBOM JSON schema validity: Validate using `jq .bomFormat fincore-backend-sbom.json` (returns `"CycloneDX"`).

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Trivy scan fails with `CRITICAL: CVE-XXXX-XXXX in alpine-baselayout`**
   - *Root Cause:* The base Alpine image used in the Dockerfile has an unpatched OS package.
   - *Remediation:* Update the base image tag in the Dockerfile to the newest alpine release, or add `RUN apk update && apk upgrade --no-cache` to the build stage.
2. **Failure Mode: Trivy scan times out downloading vulnerability database**
   - *Root Cause:* Slow network connection to GitHub container registry (`ghcr.io/aquasecurity/trivy-db`).
   - *Remediation:* Pre-cache the database or increase timeout with `--timeout 10m`.
3. **Failure Mode: Build fails on an unfixed vulnerability with no patch available**
   - *Root Cause:* A newly disclosed CVE exists with zero upstream vendor fix.
   - *Remediation:* The flag `--ignore-unfixed` is intentionally included to prevent blocking deployments on vulnerabilities where no vendor patch exists, provided compensating controls are documented.

### G. Rollback & Disaster Recovery Procedures
If a deployed container image is discovered to have a critical zero-day:
1. Scan previous image releases in ECR using Trivy to identify the latest clean version.
2. Update the GitOps repository image tag to point to the clean image.
3. Argo CD initiates an automated rolling rollback within 30 seconds.

### H. Security & Compliance Guardrails
- Scans must enforce `--exit-code 1` on High/Critical vulnerabilities in CI/CD pipelines.
- Container registries must enforce immutable image tags to prevent unauthorized overwrites of scanned images.

### I. Production vs Local Differences

| Dimension | Local Developer Workstation | Production Cloud Registry |
| :--- | :--- | :--- |
| **Scanner Execution** | Local Docker / Trivy CLI | Automated scan on push in AWS ECR via AWS Inspector |
| **Database Updates** | Downloaded dynamically | Synchronized continuously with commercial threat feeds |
| **Admission Control** | Advisory | Hard blocking via Kubernetes ImagePolicyWebhook / Kyverno |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why is an SBOM (Software Bill of Materials) mandatory in modern enterprise banking?**
  - *Answer:* An SBOM is a complete, nested machine-readable inventory of every software component, library, and license bundled inside an application. In regulated banking (enforced by US Executive Order 14028 and European DORA regulations), an SBOM enables security teams to instantly query whether an emergency zero-day (e.g. Log4Shell or OpenSSL flaw) affects any deployed microservice across the enterprise without needing to decompile or scan live clusters.
- **Q2: What is the purpose of the `--ignore-unfixed` flag in Trivy CI/CD scans?**
  - *Answer:* In public Linux distributions, some low or medium severity CVEs remain unfixed by maintainers indefinitely because they are theoretical or low-impact. Without `--ignore-unfixed`, CI/CD pipelines would be permanently blocked on issues that developers cannot remediate. This flag focuses gating strictly on actionable, fixable vulnerabilities.
- **Q3: How do you sign and verify container image provenance before deploying to Kubernetes?**
  - *Answer:* In the CI pipeline, after Trivy passes, we sign the container image using Sigstore / Cosign with an ephemeral OIDC key from the CI provider. In the Kubernetes cluster, we deploy an admission controller (e.g., Sigstore Policy Controller or Kyverno) that cryptographically verifies the image signature against our public key before allowing any pod to be scheduled.

### K. Cost & Performance Considerations
- Trivy is open-source ($0 licensing cost).
- Trivy's local caching of vulnerability databases keeps CI scan times under 15 seconds per image.

### L. Real-World Banking Considerations
- Financial institutions are frequent targets of software supply chain attacks. Storing tamper-evident SBOMs alongside container images satisfies SOC 2 Type II supply chain integrity requirements.

### M. Progress Sign-off Checklist
- [ ] `trivy-scan.sh` script reviewed.
- [ ] Backend container image scanned with 0 fixable High/Critical CVEs.
- [ ] Frontend container image scanned with 0 fixable High/Critical CVEs.
- [ ] CycloneDX SBOM generated and verified.

### N. Next Manual Transition
Proceed to **[Manual 12: Dynamic Application Security Testing (DAST) with OWASP ZAP](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/12-dast-dynamic-security-testing-zap.md)** to execute black-box penetration testing against running API endpoints.

