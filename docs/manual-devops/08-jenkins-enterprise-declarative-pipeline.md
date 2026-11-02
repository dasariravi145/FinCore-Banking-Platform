# FinCore Banking Platform — DevOps Engineering Manual
## Manual 08: Jenkins Enterprise Declarative CI/CD Pipeline Implementation

### A. Objective & Architecture Context
Continuous Integration and Continuous Delivery (CI/CD) in a banking environment must not merely build code; it must enforce strict quality, security, and compliance gates. This manual details the configuration and execution of the enterprise 10-stage declarative pipeline defined in `devops-configurations/jenkins/Jenkinsfile`.

### B. Prerequisites & Dependencies
- Jenkins Controller 2.440+ installed (locally or in Kubernetes).
- Jenkins Plugins: Docker Pipeline, SonarQube Scanner, Pipeline Stage View, Credentials Binding, Git.
- Docker daemon accessible to Jenkins agent.
- SonarQube server and AWS credentials configured in Jenkins Credentials store.

### C. Step-by-Step Implementation Instructions

#### Step 1: Inspect the 10 Pipeline Stages
Review `devops-configurations/jenkins/Jenkinsfile`:
1. **Stage 1: Checkout & Verification:** Fetches branch, extracts commit hash, and validates workspace.
2. **Stage 2: Secret Scanning:** Runs Gitleaks against recent commits; fails immediately on credential detection.
3. **Stage 3: Backend Build & Test:** Executes `mvn clean test jacoco:report` (all 84 tests executed; coverage reports generated).
4. **Stage 4: Frontend Build & Test:** Executes `npm ci`, `npm run lint`, and `npm run test` (all 20 Vitest tests executed).
5. **Stage 5: SonarQube Analysis & Quality Gate:** Scans backend and frontend; pauses pipeline with `waitForQualityGate()` until SonarQube webhook returns pass.
6. **Stage 6: Software Composition Analysis (SCA):** Runs OWASP Dependency-Check on Maven dependencies and npm audit on JavaScript packages.
7. **Stage 7: Multi-Stage Container Image Build:** Builds `fincore-backend` and `fincore-frontend` Docker images tagged with Git commit SHA and build number.
8. **Stage 8: Container Security Scanning:** Runs Trivy against built images; fails build on `CRITICAL` or unpatched vulnerabilities.
9. **Stage 9: Push to Container Registry:** Authenticates with AWS ECR or Docker Hub and pushes immutable tagged images.
10. **Stage 10: GitOps Manifest Update:** Clones GitOps repository, updates image tags in Helm `values.yaml`, and commits to trigger Argo CD sync.

#### Step 2: Configure Jenkins Credentials
In the Jenkins UI (`Manage Jenkins -> Credentials -> System -> Global credentials`), add:
- `sonar-token`: Secret text (SonarQube API authentication token).
- `aws-ecr-credentials`: AWS Credentials or IAM Role.
- `git-deploy-key`: SSH Private key for pushing updated tags to GitOps repository.

#### Step 3: Create Jenkins Multibranch Pipeline Job
1. Click **New Item** -> Enter `FinCore-Banking-Platform` -> Select **Multibranch Pipeline**.
2. Under **Branch Sources**, add **Git** -> Enter repository URL.
3. Under **Build Configuration**, select **by Jenkinsfile** -> Path: `devops-configurations/jenkins/Jenkinsfile`.
4. Click **Save** and trigger initial scan.

### D. Configuration References
- Declarative Pipeline: `devops-configurations/jenkins/Jenkinsfile`
- SonarQube properties: `devops-configurations/sonarqube/sonar-project.properties`
- Security scan scripts: `devops-configurations/security/`

### E. Verification & Validation Steps
Trigger a build on `develop` branch and inspect the stage view:
- Verify that all 10 stages render green in the Jenkins UI.
- Verify test result trend: 84 backend tests, 20 frontend tests, 0 failures.
- Check artifact archiving: JaCoCo coverage reports, OWASP dependency report, and Trivy scan logs archived in Jenkins build artifacts.

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `java.io.IOException: Cannot run program "docker"`**
   - *Root Cause:* Jenkins user does not have permission to communicate with Docker daemon socket (`/var/run/docker.sock`).
   - *Remediation:* Add Jenkins user to the `docker` group on the host (`sudo usermod -aG docker jenkins`) and restart the Jenkins service.
2. **Failure Mode: Pipeline hangs indefinitely at `waitForQualityGate()`**
   - *Root Cause:* SonarQube webhook is not configured to notify Jenkins, or network firewall blocks SonarQube from reaching Jenkins URL.
   - *Remediation:* In SonarQube (`Administration -> Configuration -> Webhooks`), add webhook pointing to `http://<jenkins-url>:8080/sonarqube-webhook/`.
3. **Failure Mode: Stage 8 Trivy scan fails with exit code 1**
   - *Root Cause:* Trivy detected an unpatched `CRITICAL` CVE in an underlying OS package.
   - *Remediation:* Update the base image in `backend.Dockerfile` or `frontend.Dockerfile` to the latest patch release (e.g., bump `alpine:3.20.3` to latest).

### G. Rollback & Disaster Recovery Procedures
If a pipeline deployment fails mid-flight:
- The pipeline `post { failure { ... } }` block triggers automated Slack/Email alerts with the failing stage name and console log URL.
- The GitOps manifest update stage (Stage 10) only executes if all preceding 9 stages pass, preventing bad artifacts from being promoted to Kubernetes.

### H. Security & Compliance Guardrails
- **Credential Masking:** All secrets injected via `withCredentials()` are masked with `****` in Jenkins console logs.
- **Ephemeral Workspaces:** Workspace is wiped clean before and after every build via `cleanWs()`.
- **Immutable Artifacts:** Images are tagged with unique Git SHAs (`fincore-backend:${GIT_COMMIT}`), never overwritten with mutable tags like `latest` in production.

### I. Production vs Local Differences

| Dimension | Local Developer Testing | Jenkins Enterprise CI Pipeline |
| :--- | :--- | :--- |
| **Execution** | Manual `mvn test` and `npm test` | Automated parallel agent execution |
| **Security Gates** | Voluntary | Mandatory blockers; zero overrides permitted |
| **Artifact Publishing** | Local Docker daemon cache | Encrypted AWS ECR registry with immutable tags |

### J. Interview Readiness Questions & Model Answers
- **Q1: What is the benefit of a Declarative Pipeline over a Scripted Pipeline in Jenkins?**
  - *Answer:* Declarative Pipelines provide a structured, opinionated syntax with built-in validation, unified `post` action blocks, native Docker agent syntax, and simplified restartability from stages. Scripted Pipelines offer raw Groovy flexibility but tend to become unmaintainable spaghetti code with inconsistent error handling across teams.
- **Q2: Why should container images be tagged with Git commit SHAs rather than build numbers or `latest`?**
  - *Answer:* Build numbers are local to a specific Jenkins server and lack traceability to the exact code change. The tag `latest` is mutable and creates nondeterministic deployments where two pods might run completely different code. Tagging with the Git commit SHA provides 100% cryptographic traceability from running container directly back to the audited commit.
- **Q3: How do you prevent pipeline execution from monopolizing Jenkins master controller resources?**
  - *Answer:* We enforce a distributed architecture where the Jenkins controller executes zero build workloads (`executors = 0` on master). All builds are dispatched to dynamic ephemeral agent pods running inside Kubernetes (`kubernetes-plugin`), which scale up on demand and terminate immediately upon stage completion.

### K. Cost & Performance Considerations
- Using ephemeral Kubernetes Jenkins agents reduces compute costs by terminating worker EC2 instances when the build queue is empty.
- Maven local repository caching via shared PVC reduces build times from 6 minutes to under 90 seconds.

### L. Real-World Banking Considerations
- PCI-DSS Requirement 6 mandates formal separation of duties between code developers and deployment systems. Jenkins pipelines automate code promotion through cryptographically verified gates without developer credentials having production deployment rights.

### M. Progress Sign-off Checklist
- [ ] Jenkins multibranch pipeline job configured.
- [ ] Credentials configured in Jenkins credentials store.
- [ ] SonarQube webhook verified.
- [ ] All 10 stages execute and pass in sequence.

### N. Next Manual Transition
Proceed to **[Manual 09: SonarQube Code Quality & Security Gates](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/09-sonarqube-code-quality-security-gates.md)** to configure static analysis rules and coverage enforcement.

