# FinCore Banking Platform — DevOps Engineering Manual
## Manual 22: Argo CD GitOps Continuous Delivery & Self-Healing

### A. Objective & Architecture Context
Traditional "Push-based" CI/CD pipelines (where Jenkins or GitHub Actions runners hold long-lived cluster-admin credentials and run `kubectl apply`) present severe attack surfaces. If the CI runner is compromised, the production cluster is compromised. GitOps inverts this model into a "Pull-based" paradigm: the Git repository is the single source of truth, and an in-cluster controller continuously synchronizes live cluster state with declared Git manifests. This manual implements GitOps for FinCore using Argo CD and the Application manifest in `devops-configurations/argocd/application.yaml`.

### B. Prerequisites & Dependencies
- Manuals 15–21 completed (EKS cluster running, Helm chart created and committed to Git).
- `kubectl` connected to EKS cluster.
- Argo CD CLI installed locally (`brew install argocd` or downloaded binary).

### C. Step-by-Step Implementation Instructions

#### Step 1: Install Argo CD in the Kubernetes Cluster
Create the dedicated `argocd` namespace and apply the official stable manifest:
```powershell
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/v2.10.4/manifests/install.yaml
```
Wait for all Argo CD controller pods to become ready:
```powershell
kubectl wait --for=condition=available deployment/argocd-server -n argocd --timeout=300s
```

#### Step 2: Access the Argo CD Web UI & Retrieve Admin Password
Retrieve the initial auto-generated admin password:
```powershell
$ARGO_PASSWORD = (kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($input)))
Write-Host "Argo CD Admin Password: $ARGO_PASSWORD"
```
Port-forward the Argo CD server to your workstation:
```powershell
kubectl port-forward svc/argocd-server -n argocd 8085:443
```
Open `https://localhost:8085` in your browser. Username: `admin`, Password: `$ARGO_PASSWORD`.

#### Step 3: Inspect the FinCore Application CRD
Review `devops-configurations/argocd/application.yaml`:
```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: fincore-banking-platform
  namespace: argocd
  finalizers:
    - resources-finalizer.argocd.argoproj.io
spec:
  project: default
  source:
    repoURL: 'https://github.com/your-org/FinCore-Banking-Platform.git'
    targetRevision: main
    path: devops-configurations/helm
    helm:
      valueFiles:
        - values.yaml
  destination:
    server: 'https://kubernetes.default.svc'
    namespace: fincore
  syncPolicy:
    automated:
      prune: true     # Automatically deletes resources removed from Git
      selfHeal: true  # Automatically reverts unauthorized manual kubectl changes
    syncOptions:
      - CreateNamespace=true
      - ServerSideApply=true
```

#### Step 4: Deploy the Argo CD Application
Update `repoURL` with your actual repository URL, then apply:
```powershell
kubectl apply -f devops-configurations/argocd/application.yaml
```

### D. Configuration References
- Application CRD: `devops-configurations/argocd/application.yaml`
- Helm Chart: `devops-configurations/helm/`
- Jenkins GitOps promotion stage: `devops-configurations/jenkins/Jenkinsfile` (Stage 10)

### E. Verification & Validation Steps
Check the application synchronization status via Argo CD CLI or `kubectl`:
```powershell
kubectl get application fincore-banking-platform -n argocd
```
*Expected Output:*
```
NAME                       SYNC STATUS   HEALTH STATUS
fincore-banking-platform   Synced        Healthy
```
View the tree of managed resources (Deployments, Services, ConfigMaps, Ingress) in the Argo CD UI. All nodes must display green checkmarks.

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `ComparisonError: x509: certificate signed by unknown authority`**
   - *Root Cause:* Argo CD cannot clone from an enterprise private Git server with custom TLS certificates.
   - *Remediation:* Add the internal CA certificate to the `argocd-tls-certs-cm` ConfigMap in `argocd` namespace.
2. **Failure Mode: `OutOfSync` state with persistent diff on ReplicaCount**
   - *Root Cause:* Horizontal Pod Autoscaler (HPA) modified replica count in live cluster, which Argo CD perceives as drift from static `values.yaml`.
   - *Remediation:* Add `ignoreDifferences` in `application.yaml` for field `spec.replicas` on Deployments when HPA is active.
3. **Failure Mode: Application fails to sync due to missing Helm values file**
   - *Root Cause:* The path in `source.helm.valueFiles` does not match the actual file path relative to `source.path`.
   - *Remediation:* Paths in `valueFiles` are evaluated relative to the chart root declared in `source.path`.

### G. Rollback & Disaster Recovery Procedures
In GitOps, rollbacks are executed through Git:
1. Revert the commit in Git: `git revert <commit-sha> && git push origin main`.
2. Argo CD detects the updated Git commit and automatically rolls back the live cluster to match the previous manifest state within 3 minutes.

### H. Security & Compliance Guardrails
- **Zero Push Access to Production:** Developers and CI/CD pipelines hold zero write access to production Kubernetes API. The CI pipeline's only action is updating image tags in Git.
- **Self-Healing Enabled:** `selfHeal: true` immediately overwrites unauthorized manual `kubectl edit` or `kubectl delete` commands, enforcing that Git is the sole source of truth.

### I. Production vs Local Differences

| Dimension | Manual CI/CD Push (`kubectl apply`) | Argo CD GitOps Pull |
| :--- | :--- | :--- |
| **Credential Storage** | CI runner holds permanent `cluster-admin` token | In-cluster controller; zero credentials outside Kubernetes |
| **Drift Management** | Untracked until next build | Continuously monitored; self-heals within seconds |
| **Audit Trail** | Fragmented CI logs | Cryptographically signed Git commit history |

### J. Interview Readiness Questions & Model Answers
- **Q1: What are the 4 core principles of GitOps?**
  - *Answer:* According to the OpenGitOps standard: (1) **Declarative:** The entire desired system state is described declaratively (YAML/HCL). (2) **Versioned and Immutable:** The desired state is stored in Git, providing an immutable versioned history. (3) **Pulled Automatically:** Software agents pull the desired state from Git automatically. (4) **Continuously Reconciled:** The agent continuously compares live state against desired state, alerting or automatically correcting configuration drift.
- **Q2: How do you prevent an infinite sync loop between Argo CD and an HPA (Horizontal Pod Autoscaler)?**
  - *Answer:* An HPA dynamically mutates the `spec.replicas` field of a Deployment in response to CPU/memory metrics. If the Git manifest declares `replicas: 2`, Argo CD detects a diff whenever HPA scales to 4 and attempts to reconcile it back to 2. We resolve this by configuring `ignoreDifferences` in the Argo CD Application CRD for `jsonPointers: ["/spec/replicas"]` on Deployments, delegating replica count authority to the HPA while managing all other configuration via GitOps.
- **Q3: What is the `selfHeal` feature in Argo CD and why is it vital for financial infrastructure?**
  - *Answer:* In high-stress production outages, engineers might be tempted to manually alter live cluster resources using `kubectl edit` without documenting changes in Git. With `selfHeal: true`, Argo CD detects the drift within seconds and automatically re-applies the Git manifest, actively destroying unauthorized manual edits. This enforces strict change-management compliance and prevents untracked snowflake configurations.

### K. Cost & Performance Considerations
- Argo CD runs efficiently inside the cluster, consuming ~0.5 vCPU and 512 MB RAM across its controller pods.

### L. Real-World Banking Considerations
- Financial regulatory audits (such as FFIEC or MAS TRM) require proof that all changes to production environments followed strict Maker-Checker approval controls. GitOps provides a tamper-proof audit trail where every production change maps directly to an approved, dual-authorized Pull Request.

### M. Progress Sign-off Checklist
- [ ] Argo CD installed in `argocd` namespace.
- [ ] Initial admin credentials retrieved and verified.
- [ ] `application.yaml` applied.
- [ ] FinCore application status reports `Synced` and `Healthy`.

### N. Next Manual Transition
Proceed to **[Manual 23: Prometheus Metrics Collection & Banking Alert Rules](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/23-prometheus-metrics-collection-alerting.md)** to begin Part 5 (Observability, Logging, Tracing & Site Reliability Engineering).

