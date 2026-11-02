# FinCore Banking Platform — DevOps Engineering Manual
## Manual 28: DevSecOps Secrets Management with HashiCorp Vault & Dynamic Credentials

### A. Objective & Architecture Context
Static credentials (such as long-lived database passwords stored in Kubernetes Secrets or environment variables) present significant attack vectors: if leaked, they remain valid indefinitely until manual human intervention. In a PCI-DSS compliant banking platform, static passwords must be replaced by dynamic, short-lived, automatically rotating credentials. This manual implements HashiCorp Vault with Kubernetes Agent Injection, enabling the FinCore backend to authenticate via Kubernetes ServiceAccount tokens and receive dynamic PostgreSQL credentials with a 1-hour Time-to-Live (TTL).

### B. Prerequisites & Dependencies
- Manuals 18–19 completed (Kubernetes cluster with RBAC active).
- HashiCorp Vault Helm chart repository added (`helm repo add hashicorp https://helm.releases.hashicorp.com`).
- Aurora PostgreSQL cluster accessible from Vault.

### C. Step-by-Step Implementation Instructions

#### Step 1: Install HashiCorp Vault in Kubernetes
Install Vault with the Agent Injector enabled:
```powershell
helm install vault hashicorp/vault `
  --namespace vault `
  --create-namespace `
  --set "injector.enabled=true" `
  --set "server.ha.enabled=false"
```
Initialize and unseal Vault (or use AWS KMS Auto-Unseal in production):
```powershell
kubectl exec -n vault vault-0 -- vault operator init -key-shares=1 -key-threshold=1 -format=json > vault-keys.json
$UNSEAL_KEY = (Get-Content vault-keys.json | ConvertFrom-Json).unseal_keys_b64[0]
$ROOT_TOKEN = (Get-Content vault-keys.json | ConvertFrom-Json).root_token

kubectl exec -n vault vault-0 -- vault operator unseal $UNSEAL_KEY
```

#### Step 2: Enable Kubernetes Authentication in Vault
Configure Vault to verify Kubernetes ServiceAccount tokens:
```powershell
kubectl exec -n vault vault-0 -- vault login $ROOT_TOKEN
kubectl exec -n vault vault-0 -- vault auth enable kubernetes
kubectl exec -n vault vault-0 -- vault write auth/kubernetes/config `
  kubernetes_host="https://kubernetes.default.svc"
```

#### Step 3: Configure Vault Dynamic Database Secrets Engine
Enable the database secrets engine and configure the PostgreSQL connection:
```powershell
kubectl exec -n vault vault-0 -- vault secrets enable database

kubectl exec -n vault vault-0 -- vault write database/config/fincore-postgres `
  plugin_name=postgresql-database-plugin `
  allowed_roles="fincore-backend-role" `
  connection_url="postgresql://{{username}}:{{password}}@fincore-aurora-cluster.us-east-1.rds.amazonaws.com:5432/fincore_db?sslmode=require" `
  username="fincore_admin" `
  password="AdminMasterPasswordFromSecretsManager!"
```

Create the dynamic role with automated 1-hour TTL:
```powershell
kubectl exec -n vault vault-0 -- vault write database/roles/fincore-backend-role `
  db_name=fincore-postgres `
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO \"{{name}}\";" `
  default_ttl="1h" `
  max_ttl="24h"
```

#### Step 4: Inject Vault Sidecar into FinCore Backend Deployment
Add Vault Agent annotations to `devops-configurations/kubernetes/backend-deployment.yaml`:
```yaml
spec:
  template:
    metadata:
      annotations:
        vault.hashicorp.com/agent-inject: "true"
        vault.hashicorp.com/role: "fincore-backend-role"
        vault.hashicorp.com/agent-inject-secret-db-creds.txt: "database/creds/fincore-backend-role"
        vault.hashicorp.com/agent-inject-template-db-creds.txt: |
          {{- with secret "database/creds/fincore-backend-role" -}}
          SPRING_DATASOURCE_USERNAME={{ .Data.username }}
          SPRING_DATASOURCE_PASSWORD={{ .Data.password }}
          {{- end -}}
```
When deployed, the Vault Agent sidecar automatically authenticates, requests dynamic credentials from Vault, writes them to `/vault/secrets/db-creds.txt`, and seamlessly rotates them before the 1-hour TTL expires.

### D. Configuration References
- Backend Deployment annotations: `devops-configurations/kubernetes/backend-deployment.yaml`
- Vault Role Definition: `devops-configurations/security/vault-policy.hcl`
- Database Credentials spec: `devops-configurations/kubernetes/secrets-template.yaml`

### E. Verification & Validation Steps
Inspect the running backend pod to confirm that the Vault sidecar is injected:
```powershell
kubectl get pods -n fincore -l app=fincore-backend
```
*Expected Output:* Container count shows `2/2` (fincore-backend container + vault-agent sidecar).

Inspect generated dynamic database credentials:
```powershell
kubectl exec -n fincore deploy/fincore-backend -c fincore-backend -- cat /vault/secrets/db-creds.txt
```
*Expected Output:*
```
SPRING_DATASOURCE_USERNAME=v-kubernet-fincore-ba-xxxxxxxxxxxx
SPRING_DATASOURCE_PASSWORD=A1b2C3d4E5f6G7h8...
```
Verify that the generated user exists in PostgreSQL:
```sql
SELECT usename, valuntil FROM pg_user WHERE usename LIKE 'v-kubernet%';
```

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Backend container stuck in `Init:0/1`**
   - *Root Cause:* Vault Agent sidecar failed to authenticate against Kubernetes API or the Vault role name does not match the ServiceAccount.
   - *Remediation:* Check sidecar init logs: `kubectl logs <pod-name> -c vault-agent-init -n fincore`. Ensure the ServiceAccount `fincore-backend` is bound to `fincore-backend-role` in Vault.
2. **Failure Mode: Database connections break after 60 minutes**
   - *Root Cause:* Vault revoked the temporary role, but Spring Boot HikariCP did not re-read the updated credentials file.
   - *Remediation:* Configure Spring Cloud Vault or a file watcher script in the sidecar that triggers `/actuator/refresh` or gracefully restarts the pod upon credential renewal.
3. **Failure Mode: Vault sealed after node reboot**
   - *Root Cause:* Vault uses Shamir's Secret Sharing in memory; rebooting drops unseal keys.
   - *Remediation:* Enable AWS KMS Auto-Unseal in `vault-config.yaml` (`seal "awskms" { ... }`) to allow Vault to unseal automatically without human intervention.

### G. Rollback & Disaster Recovery Procedures
To emergency-revoke all dynamic credentials if an unauthorized access attempt is detected:
```powershell
kubectl exec -n vault vault-0 -- vault lease revoke -prefix database/creds/fincore-backend-role
```
Vault immediately executes `DROP ROLE` statements in PostgreSQL, terminating all active hijacked database sessions instantly.

### H. Security & Compliance Guardrails
- **Zero Static Database Passwords:** Application developers, operators, and CI runners never know or store the database password.
- **Short TTLs:** 1-hour credentials limit the window of opportunity for intercepted credentials.
- **Audit Logging:** Every credential issuance and lease revocation is logged to an immutable Vault audit log device.

### I. Production vs Local Differences

| Dimension | Local Staging (Static Secrets) | Production Enterprise Vault |
| :--- | :--- | :--- |
| **Credential Lifetime** | Permanent (never expires) | Ephemeral (1-hour auto-rotating lease) |
| **Revocation** | Manual database user deletion | Instant programmatic revocation via API |
| **Encryption at Rest** | Base64 in Kubernetes Secrets | Encrypted using AES-256-GCM backed by hardware HSM / AWS KMS |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why are dynamic database credentials superior to static Kubernetes Secrets?**
  - *Answer:* Static Kubernetes Secrets are merely Base64-encoded strings stored in etcd. If an attacker gains read access to the namespace or exfiltrates a backup, the static credentials remain valid indefinitely. Dynamic credentials generated by Vault are unique per pod, have an ephemeral TTL (e.g. 1 hour), and are automatically dropped from the database when the lease expires or the pod terminates, reducing the attack blast radius to virtually zero.
- **Q2: How does the Vault Agent Injector sidecar pattern work?**
  - *Answer:* The Vault Agent Injector is a Kubernetes Mutating Admission Webhook. When a pod with annotations (`vault.hashicorp.com/agent-inject: "true"`) is submitted to the API server, the webhook intercepts the pod spec and injects an init container and a sidecar container running `vault-agent`. The init container uses the pod's projected ServiceAccount token to authenticate against Vault, fetches the requested secrets, and renders them into an in-memory `emptyDir` volume shared with the application container. The sidecar then continuously manages lease renewals.
- **Q3: What is the Transit Secrets Engine in Vault and how does it support PCI-DSS field-level encryption?**
  - *Answer:* The Transit Secrets Engine provides "Cryptography as a Service". Instead of managing encryption keys inside Java code (which risks key leakage via heap dumps), the application sends sensitive plaintext (like Social Security numbers or credit card PANs) to Vault over mTLS. Vault encrypts the data using AES-GCM-256 with managed master keys and returns ciphertext (`vault:v1:...`) to be stored in PostgreSQL. The application stores only ciphertext; even a full database breach yields zero usable customer financial data.

### K. Cost & Performance Considerations
- HashiCorp Vault Open Source is free ($0 license).
- Memory overhead: Vault Agent sidecar consumes < 25 MB RAM per pod.

### L. Real-World Banking Considerations
- PCI-DSS 4.0 Requirement 8.3.6 mandates periodic rotation of authentication credentials. Dynamic Vault rotation fulfills this requirement continuously every 60 minutes rather than manually every 90 days.

### M. Progress Sign-off Checklist
- [ ] Vault installed and unsealed in `vault` namespace.
- [ ] Kubernetes authentication backend enabled.
- [ ] PostgreSQL dynamic database engine configured with 1-hour TTL.
- [ ] Backend pod successfully boots with dynamic credentials injected.

### N. Next Manual Transition
Proceed to **[Manual 29: MLOps Fraud Detection Model Governance & Lifecycle](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/29-mlops-fraud-detection-model-lifecycle.md)** to govern machine learning pipelines and synthetic fraud detection models.

