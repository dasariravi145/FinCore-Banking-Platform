# FinCore Banking Platform — DevOps Engineering Manual
## Manual 09: SonarQube Code Quality & Enterprise Security Gates

### A. Objective & Architecture Context
In financial software engineering, code quality directly impacts security, transaction reliability, and audit compliance. Subtle logic bugs in balance calculations, unclosed JDBC connections, or improper null handling can cause financial discrepancy. This manual establishes SonarQube static application security testing (SAST) for both the Java 21 backend and React/TypeScript frontend, enforcing strict Quality Gates before any code merges into `develop` or `main`.

### B. Prerequisites & Dependencies
- SonarQube Community or Enterprise 10.x running (locally via Docker or cloud instance).
- SonarScanner CLI installed or executed via Maven `sonar-maven-plugin`.
- JaCoCo code coverage report generated from `mvn clean test`.
- Vitest coverage LCOV report generated from `npm run test:coverage`.

### C. Step-by-Step Implementation Instructions

#### Step 1: Review SonarQube Project Configuration
Inspect `devops-configurations/sonarqube/sonar-project.properties`:
```properties
sonar.projectKey=fincore-banking-platform
sonar.projectName=FinCore Banking Platform
sonar.projectVersion=1.0.0

# Multi-module / Multi-language paths
sonar.sources=src/main/java,frontend/src
sonar.tests=src/test/java,frontend/src/test
sonar.java.binaries=target/classes

# Java Coverage (JaCoCo)
sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml

# TypeScript / React Coverage (LCOV)
sonar.javascript.lcov.reportPaths=frontend/coverage/lcov.info

# Exclusions (Generated code, Flyway migrations, build configs)
sonar.exclusions=**/db/migration/**,**/dist/**,**/node_modules/**,**/*.config.ts
```

#### Step 2: Define the FinCore Banking Quality Gate
In the SonarQube Web UI (`Quality Gates -> Create`), define `FinCore-Banking-Standard`:
1. **Security Vulnerabilities:** 0 allowed on New Code (Rating: A).
2. **Security Hotspots Reviewed:** 100% required.
3. **Bugs:** 0 Blocker, 0 Critical allowed on New Code.
4. **Code Coverage on New Code:** Minimum 80.0% required.
5. **Duplicated Lines on New Code:** Maximum 3.0% allowed.
6. Set as Default Quality Gate for project `fincore-banking-platform`.

#### Step 3: Execute Local SonarQube Scan
If running SonarQube locally on port 9000:
```powershell
mvn clean verify sonar:sonar `
  -Dsonar.host.url="http://localhost:9000" `
  -Dsonar.token="sqp_your_generated_sonar_token_here"
```

#### Step 4: Configure Jenkins Webhook Notification
1. Navigate to SonarQube -> **Administration** -> **Configuration** -> **Webhooks**.
2. Click **Create** -> Name: `Jenkins-FinCore-CI` -> URL: `http://<jenkins-host>:8080/sonarqube-webhook/`.
3. In `devops-configurations/jenkins/Jenkinsfile`, verify the gate check:
```groovy
stage('SonarQube Analysis') {
    steps {
        withSonarQubeEnv('SonarQube') {
            sh "mvn sonar:sonar"
        }
        timeout(time: 10, unit: 'MINUTES') {
            script {
                def qg = waitForQualityGate()
                if (qg.status != 'OK') {
                    error "Pipeline aborted due to Quality Gate failure: ${qg.status}"
                }
            }
        }
    }
}
```

### D. Configuration References
- Properties template: `devops-configurations/sonarqube/sonar-project.properties`
- Jenkins pipeline: `devops-configurations/jenkins/Jenkinsfile` (Stage 5)

### E. Verification & Validation Steps
Log in to the SonarQube dashboard and confirm:
- Overall status shows **PASSED** (Green badge).
- 84 backend unit/integration tests successfully analyzed.
- Line coverage meets or exceeds 80%.
- Security Rating: **A** (0 vulnerabilities, 0 injection flaws).

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `Coverage is 0.0%` despite tests passing**
   - *Root Cause:* JaCoCo plugin was not configured in `pom.xml`, or the `sonar.coverage.jacoco.xmlReportPaths` path does not point to the actual XML output.
   - *Remediation:* Verify that `target/site/jacoco/jacoco.xml` exists on disk before running the scanner.
2. **Failure Mode: Scanner fails with `Class not found during bytecode analysis`**
   - *Root Cause:* `sonar.java.binaries` points to an empty directory because `mvn test` or `mvn compile` was not run prior to scanning.
   - *Remediation:* Always run `mvn compile test` before triggering `sonar:sonar`.
3. **Failure Mode: Pipeline aborts with `Quality Gate failure: ERROR` due to code duplication**
   - *Root Cause:* Duplicate code blocks in DTO mappings or controllers exceeded the 3% threshold.
   - *Remediation:* Refactor duplicate mapping logic into shared utility methods or record mappers.

### G. Rollback & Disaster Recovery Procedures
If a false-positive rule flags valid banking code (e.g., hardcoded test seed accounts in migration scripts):
1. In SonarQube UI, inspect the issue and mark as **False Positive** or **Won't Fix** with documented architectural justification.
2. If necessary, tune exclusions in `sonar-project.properties` without lowering global gate criteria.

### H. Security & Compliance Guardrails
- SonarQube server must be accessed over HTTPS with mandatory SSO / MFA for administrators.
- API tokens generated for Jenkins must follow least-privilege project analysis permissions.

### I. Production vs Local Differences

| Dimension | Local Developer Analysis | Centralized SonarQube Server |
| :--- | :--- | :--- |
| **Tool** | SonarLint IDE Plugin | Dedicated SonarQube Enterprise instance |
| **Feedback** | As-you-type in IDE editor | Centralized dashboard tracking debt trends over time |
| **Gate Enforcement** | Advisory only | Hard CI/CD blocker halting pull request merges |

### J. Interview Readiness Questions & Model Answers
- **Q1: What is the difference between a Security Vulnerability and a Security Hotspot in SonarQube?**
  - *Answer:* A Vulnerability is a definite security flaw that requires immediate remediation (e.g., SQL injection, hardcoded private key). A Security Hotspot highlights security-sensitive code (e.g., random number generation, cryptographic hashing, CORS configuration) that may be entirely intentional but requires manual human security review to confirm safety.
- **Q2: Why is the `waitForQualityGate()` webhook architecture superior to polling SonarQube from Jenkins?**
  - *Answer:* Polling wastes Jenkins worker thread cycles, consumes network bandwidth, and risks timeout errors if the SonarQube background compute queue is busy. Using webhooks allows the Jenkins pipeline to sleep asynchronously until SonarQube finishes processing the report and actively wakes Jenkins up with the final pass/fail decision.
- **Q3: How do you prevent developers from gaming code coverage metrics with meaningless tests?**
  - *Answer:* We enforce branch coverage alongside line coverage, disallow tests without assertions via custom architectural linter rules (`ArchUnit`), and require peer code reviews on all test additions to ensure tests validate business edge cases (such as overdrafts and concurrency locks) rather than trivial getters and setters.

### K. Cost & Performance Considerations
- SonarQube Community Edition is open source ($0 licensing cost).
- Offloading scan execution to local agents keeps server CPU utilization under 20%.

### L. Real-World Banking Considerations
- Banking software must comply with CERT Java Coding Standards. SonarQube rulesets ensure compliance by preventing serialization vulnerabilities, thread safety violations, and precision loss in currency floating-point operations.

### M. Progress Sign-off Checklist
- [ ] `sonar-project.properties` configured.
- [ ] Quality gate `FinCore-Banking-Standard` active.
- [ ] JaCoCo and Vitest coverage reports ingested.
- [ ] Quality gate status confirmed GREEN.

### N. Next Manual Transition
Proceed to **[Manual 10: Dependency Vulnerability Scanning with OWASP](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/10-dependency-vulnerability-scanning-owasp.md)** to scan software supply chains and third-party libraries.

