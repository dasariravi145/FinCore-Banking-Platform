# FinCore Banking Platform — DevOps Engineering Manual
## Manual 07: Git Branching Strategy, Conventional Commits & Pre-Commit Security Hooks

### A. Objective & Architecture Context
In enterprise banking systems, untracked code modifications, unstructured commit logs, or accidental credential leakage into version control present catastrophic compliance and security risks. This manual establishes the GitFlow branching model, enforces the Conventional Commits specification, configures automated pre-commit secret detection hooks via Gitleaks, and defines production branch protection rules for the FinCore repository.

### B. Prerequisites & Dependencies
- Git 2.40+ installed on workstation.
- Gitleaks CLI installed or run via Docker.
- FinCore local Git repository initialized.

### C. Step-by-Step Implementation Instructions

#### Step 1: Establish GitFlow Branching Model
Initialize the core branches:
```powershell
git checkout -b develop
git push -u origin develop
```
- `main`: Production-ready code; matches currently deployed production cluster state.
- `develop`: Integration branch for finished features; continuously deployed to staging.
- `feature/<ticket-id>-<short-description>`: Branched from `develop`, merged back via PR.
- `release/v<version>`: Release stabilization branch; merges to `main` and back to `develop`.
- `hotfix/<ticket-id>`: Emergency production fix; branched from `main`, merged to both `main` and `develop`.

#### Step 2: Enforce Conventional Commits
All commits must strictly follow the format:
`<type>(<scope>): <short description>`
Supported types:
- `feat`: A new feature (e.g. `feat(ledger): add multi-currency exchange rate calculation`)
- `fix`: A bug fix (e.g. `fix(auth): handle expired refresh token race condition`)
- `security`: Security enhancement (e.g. `security(deps): bump spring-security to 6.4.2`)
- `chore`: Maintenance / tooling (e.g. `chore(docker): update temurin base image to 21.0.5`)
- `refactor`: Code refactoring without behavior change (e.g. `refactor(loan): simplify amortization schedule calculation`)

#### Step 3: Install Git Pre-Commit Secret Scanner Hook
Copy Gitleaks configuration into repo:
```powershell
Copy-Item devops-configurations/security/gitleaks.toml .gitleaks.toml
```
Create `.git/hooks/pre-commit`:
```bash
#!/bin/sh
# FinCore Banking Platform — Pre-Commit Secret Detection Hook
echo "Running Gitleaks secret scan on staged files..."

if command -v gitleaks >/dev/null 2>&1; then
    gitleaks protect --staged --verbose --config=.gitleaks.toml
else
    echo "WARNING: Gitleaks not installed locally. Running fallback Docker container scan..."
    docker run --rm -v "${PWD}:/path" zricethezav/gitleaks:latest protect --source="/path" --staged --verbose
fi

if [ $? -ne 0 ]; then
    echo "=========================================================="
    echo "COMMIT REJECTED: Sensitive credentials or secrets detected!"
    echo "Review the staged files and sanitize secrets before committing."
    echo "=========================================================="
    exit 1
fi
```
Make the hook executable (in Git Bash / WSL):
```bash
chmod +x .git/hooks/pre-commit
```

#### Step 4: Configure Enterprise GitHub/GitLab Branch Protection Rules
Configure the following branch protection settings on `main` and `develop`:
1. Require pull request reviews before merging (Minimum: 2 approvals).
2. Require status checks to pass before merging:
   - `build-and-test` (Unit + Integration tests)
   - `sonarqube/quality-gate` (Code quality & coverage)
   - `security/trivy-scan` (Container vulnerability check)
   - `security/gitleaks` (Secret scanning)
3. Require signed commits (GPG / SSH commit signing).
4. Require linear history (Disallow merge commits on `main`; enforce squash & merge or rebase).
5. Enforce protections for administrators (No bypassing rules).

### D. Configuration References
- Gitleaks Rules: `devops-configurations/security/gitleaks.toml`
- Git Ignore: `.gitignore` and `devops-configurations/docker/.dockerignore`

### E. Verification & Validation Steps
Simulate an accidental secret leak to verify the pre-commit hook:
```powershell
# Create a dummy staged file with a fake secret
"AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY" > test-leak.txt
git add test-leak.txt
git commit -m "chore: test secret scanner"
```
*Expected Result:* Git commit must be aborted with an error message: `COMMIT REJECTED: Sensitive credentials or secrets detected!`.
Clean up the test:
```powershell
git reset HEAD test-leak.txt
Remove-Item test-leak.txt
```

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Pre-commit hook does not execute on Windows**
   - *Root Cause:* Git on Windows requires a valid bash interpreter in path or hook script format issue.
   - *Remediation:* Ensure the hook uses UNIX line endings (`LF`, not `CRLF`). Run `dos2unix .git/hooks/pre-commit`.
2. **Failure Mode: Gitleaks flags legitimate synthetic test passwords in test classes**
   - *Root Cause:* Standard regex matches dummy passwords in `src/test/`.
   - *Remediation:* Add path exclusions to `[whitelist]` section in `.gitleaks.toml` for `src/test/**`.
3. **Failure Mode: Developer accidentally commits with `--no-verify`**
   - *Root Cause:* Developer bypassed local pre-commit hook.
   - *Remediation:* Ensure the server-side CI/CD pipeline (Manual 08) runs Gitleaks as an independent mandatory gate.

### G. Rollback & Disaster Recovery Procedures
If a secret is accidentally committed and pushed to remote:
1. Revoke and rotate the exposed credential in AWS/Vault immediately.
2. Use `git filter-repo` or BFG Repo-Cleaner to purge the commit from Git history:
   ```powershell
   git filter-repo --invert-paths --path <leaked-file>
   git push origin --force --all
   ```

### H. Security & Compliance Guardrails
- Commit signing via GPG key guarantees non-repudiation (PCI-DSS requirement).
- No API keys, JWT secrets, database passwords, or private TLS keys may exist in Git history.

### I. Production vs Local Differences

| Dimension | Local Developer Repository | Production Central Repository |
| :--- | :--- | :--- |
| **Commit Check** | Client-side Git pre-commit hook | Server-side Webhook & CI pipeline scan |
| **Enforcement** | Can be bypassed with `--no-verify` | Hard blocker; impossible to bypass |
| **Signing** | Developer workstation GPG key | Enforced via branch protection settings |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why is client-side secret scanning alone insufficient for enterprise DevSecOps?**
  - *Answer:* Client-side Git hooks are executed on developer workstations and can be easily bypassed using `git commit --no-verify` or ignored by developers cloning onto fresh machines. True DevSecOps requires defense-in-depth: client-side pre-commit hooks for immediate developer feedback (shift-left), backed by mandatory, unbypassable server-side scanning in the CI/CD pipeline before code merges.
- **Q2: What is the difference between GitFlow and Trunk-Based Development, and which did you select for FinCore?**
  - *Answer:* Trunk-Based Development relies on short-lived feature branches (< 1-2 days) merged directly to `main` with feature flags. GitFlow utilizes separate `develop`, `release`, and `main` branches. In regulated banking environments where releases require formal audit approvals, change advisory board (CAB) reviews, and scheduled release windows, GitFlow provides explicit governance and isolation between ongoing feature development and release stabilization.
- **Q3: How do Conventional Commits streamline automated release engineering?**
  - *Answer:* Conventional Commits enable automated semantic versioning (SemVer) and automated changelog generation. By parsing prefixes (`feat:` triggers MINOR bumps, `fix:` triggers PATCH bumps, and `BREAKING CHANGE:` triggers MAJOR bumps), tools like `semantic-release` or standard-version automate release tagging without manual human error.

### K. Cost & Performance Considerations
- Gitleaks execution time on staged files is < 300 milliseconds, adding negligible overhead to the developer commit loop.

### L. Real-World Banking Considerations
- Financial institutions must produce tamper-proof audit trails for every line of code deployed to production. Signed commits linked to Jira tickets and approved GitHub PRs satisfy ISO 27001 and SOC 2 Type II audit requirements.

### M. Progress Sign-off Checklist
- [ ] GitFlow branches `main` and `develop` created.
- [ ] Pre-commit hook installed and verified.
- [ ] Gitleaks successfully blocks synthetic secret commit.
- [ ] Conventional Commits guidelines adopted.

### N. Next Manual Transition
Proceed to **[Manual 08: Jenkins Enterprise Declarative Pipeline](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/08-jenkins-enterprise-declarative-pipeline.md)** to configure the automated CI/CD engine.

