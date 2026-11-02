# FinCore Banking Platform — DevOps Engineering Manual
## Manual 21: Helm Packaging, Templating & Multi-Environment Values Strategy

### A. Objective & Architecture Context
Managing static Kubernetes YAML manifests across multiple environments (Development, Staging, Production) results in copy-paste errors, configuration divergence, and manual editing risks. Helm is the standard package manager for Kubernetes. This manual packages the FinCore banking manifests into a production Helm chart (`devops-configurations/helm/`), implements an environment-specific values strategy (`values-dev.yaml` vs `values-prod.yaml`), and demonstrates template rendering, linting, and automated release lifecycle management.

### B. Prerequisites & Dependencies
- Helm 3.14+ installed on workstation.
- Kubernetes cluster available or local dry-run capability.
- FinCore Kubernetes manifests reviewed from Manual 18.

### C. Step-by-Step Implementation Instructions

#### Step 1: Inspect the FinCore Helm Chart Structure
Review `devops-configurations/helm/`:
```
devops-configurations/helm/
├── Chart.yaml                  # Chart metadata and versioning
├── values.yaml                 # Base / Default values
├── values-staging.yaml         # Staging overrides (2 replicas, t3.medium sizing)
├── values-prod.yaml            # Production overrides (4 replicas, strict PDB)
└── templates/                  # Parameterized Kubernetes manifests
    ├── _helpers.tpl            # Named template snippets and labels
    ├── backend-deployment.yaml # Templated Backend Deployment
    ├── backend-service.yaml    # Templated Backend Service
    ├── frontend-deployment.yaml# Templated Frontend Deployment
    ├── frontend-service.yaml   # Templated Frontend Service
    ├── ingress.yaml            # Templated Ingress with conditional TLS
    ├── hpa.yaml                # Templated Horizontal Pod Autoscaler
    └── pdb.yaml                # Templated Pod Disruption Budget
```

#### Step 2: Inspect `Chart.yaml` Metadata
Review `devops-configurations/helm/Chart.yaml`:
```yaml
apiVersion: v2
name: fincore-banking-platform
description: Enterprise Helm chart for FinCore Banking Platform
type: application
version: 1.0.0
appVersion: "1.0.0"
maintainers:
  - name: FinCore Platform Engineering Team
```

#### Step 3: Lint the Chart for Syntax and Best Practices
Execute from project root:
```powershell
helm lint devops-configurations/helm
```
*Expected Output:* `1 chart(s) linted, 0 chart(s) failed`

#### Step 4: Validate Template Rendering with Dry-Run
Render the manifests locally without connecting to a cluster to verify variable interpolation:
```powershell
helm template fincore-release devops-configurations/helm `
  -f devops-configurations/helm/values.yaml `
  --namespace fincore > rendered-manifests.yaml
```
Inspect `rendered-manifests.yaml` to ensure all `{{ .Values.* }}` placeholders resolved into valid Kubernetes YAML.

#### Step 5: Install or Upgrade Helm Release
Deploy to the cluster:
```powershell
helm upgrade --install fincore devops-configurations/helm `
  --namespace fincore `
  --create-namespace `
  -f devops-configurations/helm/values.yaml
```

### D. Configuration References
- Chart metadata: `devops-configurations/helm/Chart.yaml`
- Values definition: `devops-configurations/helm/values.yaml`
- Rendered templates: `devops-configurations/helm/templates/`

### E. Verification & Validation Steps
Check the status of the deployed release:
```powershell
helm status fincore -n fincore
```
Check release revision history:
```powershell
helm history fincore -n fincore
```
*Expected Output:*
```
REVISION  UPDATED                   STATUS    CHART                           APP VERSION  DESCRIPTION
1         Mon Nov 02 01:45:00 2026  deployed  fincore-banking-platform-1.0.0  1.0.0        Install complete
```

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `Error: UPGRADE FAILED: YAML parse error on ...`**
   - *Root Cause:* Improper indentation in a Go template block (e.g. `{{- if ... }}`).
   - *Remediation:* Use `helm template --debug` to pinpoint the exact line number where invalid YAML was generated.
2. **Failure Mode: `Error: rendered manifests contain a resource that already exists`**
   - *Root Cause:* A Kubernetes resource was previously created via raw `kubectl apply` and is not tracked in Helm release metadata.
   - *Remediation:* Add Helm ownership annotations/labels to the existing resource, or delete the untracked resource before running `helm upgrade`.
3. **Failure Mode: Secret values exposed in rendered templates**
   - *Root Cause:* Plaintext passwords declared directly in `values.yaml`.
   - *Remediation:* Keep `values.yaml` clean of secrets; inject secrets via external secret stores (Vault / AWS Secrets Manager) or pass secrets via `--set-string` from secure CI runner memory.

### G. Rollback & Disaster Recovery Procedures
If a Helm deployment causes a failure, roll back to the exact previous working revision with a single atomic command:
```powershell
helm rollback fincore 1 -n fincore
```
Helm restores the previous deployment, replica counts, and config maps in seconds.

### H. Security & Compliance Guardrails
- **Helm Secret Masking:** Never commit real database passwords or JWT signing keys into `values.yaml` in Git.
- **Chart Version Immutability:** Once a chart version (e.g. `1.0.0`) is published to a chart repository, it must never be overwritten; any modification requires incrementing `version` in `Chart.yaml`.

### I. Production vs Local Differences

| Dimension | Local Developer Values (`values.yaml`) | Production Values (`values-prod.yaml`) |
| :--- | :--- | :--- |
| **Backend Replicas** | 1 or 2 replicas | 4 to 8 replicas with HPA scaling up to 20 |
| **Resource Limits** | Low limits (CPU: 500m, RAM: 512Mi) | High limits (CPU: 2000m, RAM: 2048Mi) |
| **Ingress TLS** | Self-signed or plain HTTP | Production ACM Certificate with AWS WAF |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why is Helm preferred over raw `kubectl apply -f` in enterprise deployment workflows?**
  - *Answer:* Raw manifests do not support parameterization, leading to duplicated manifest directories per environment. Helm provides powerful Go templating, multi-environment values overrides, atomic transactions (`--atomic`), automated revision tracking (`helm history`), and instant one-command rollbacks (`helm rollback`), providing release governance that raw manifests lack.
- **Q2: What is the difference between `version` and `appVersion` in `Chart.yaml`?**
  - *Answer:* `version` represents the semantic version of the Helm chart itself (the templating logic, manifests, and helper functions). `appVersion` represents the version of the underlying application software being deployed (the FinCore banking JAR or container image tag). A change to the Helm template increments `version`, while a code commit in the backend increments `appVersion`.
- **Q3: How do you handle database migrations when using Helm?**
  - *Answer:* In Helm, we use `pre-install` and `pre-upgrade` hooks to execute a dedicated Kubernetes `Job` that runs Flyway migrations before the new application pods are scheduled. If the migration job fails, Helm's `--atomic` flag aborts the upgrade and prevents the new application pods from rolling out against an incompatible schema.

### K. Cost & Performance Considerations
- Helm is a client-side CLI tool ($0 server overhead; no persistent cluster pods required).

### L. Real-World Banking Considerations
- Banking release governance requires strict verification of every deployment artifact. Helm charts packaged as OCI artifacts in AWS ECR can be signed with Cosign to guarantee that only cryptographically certified release bundles are deployed to production clusters.

### M. Progress Sign-off Checklist
- [ ] Helm chart structure created.
- [ ] `helm lint` executed with 0 errors.
- [ ] Dry-run template rendering verified.
- [ ] Helm release status confirmed deployed.

### N. Next Manual Transition
Proceed to **[Manual 22: Argo CD GitOps Continuous Delivery](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/22-argocd-gitops-continuous-delivery.md)** to implement automated GitOps reconciliation.

