# FinCore Banking Platform — DevOps Engineering Manual
## Manual 19: Kubernetes Security, RBAC & Zero-Trust NetworkPolicies

### A. Objective & Architecture Context
By default, Kubernetes implements a flat network topology where any pod in any namespace can establish unauthenticated TCP connections to any other pod or external IP. In a banking platform, a compromised frontend pod could directly probe the database or establish an outbound command-and-control (C2) connection to exfiltrate financial data. This manual establishes a Zero-Trust network architecture using Kubernetes `NetworkPolicy` objects and implements least-privilege Role-Based Access Control (RBAC).

### B. Prerequisites & Dependencies
- Manual 18 completed (`fincore` workloads running in EKS).
- Kubernetes CNI plugin supporting NetworkPolicies active (AWS VPC CNI with Network Policy agent enabled, or Calico/Cilium).

### C. Step-by-Step Implementation Instructions

#### Step 1: Enforce Pod Security Standards on Namespace
Label the `fincore` namespace to enforce the Kubernetes `Restricted` Pod Security Standard:
```powershell
kubectl label --overwrite namespace fincore `
  pod-security.kubernetes.io/enforce=restricted `
  pod-security.kubernetes.io/enforce-version=latest `
  pod-security.kubernetes.io/audit=restricted `
  pod-security.kubernetes.io/warn=restricted
```
This automatically blocks any future pods attempting to run as root, mount host namespaces, or escalate privileges.

#### Step 2: Inspect Zero-Trust NetworkPolicy Architecture
Review `devops-configurations/kubernetes/networkpolicy.yaml`:
1. **Policy 1: `default-deny-all`:** Blocks all unsolicited ingress and egress traffic for all pods in `fincore` namespace unless explicitly permitted.
2. **Policy 2: `frontend-network-policy`:**
   - Ingress: Allows TCP port 80 strictly from the AWS Load Balancer Controller / Ingress namespace.
   - Egress: Allows TCP port 8080 strictly to `fincore-backend` pods.
3. **Policy 3: `backend-network-policy`:**
   - Ingress: Allows TCP port 8080 strictly from `fincore-frontend` pods and Prometheus monitoring pods.
   - Egress:
     - Allows UDP/TCP port 53 to `kube-system` CoreDNS for internal name resolution.
     - Allows TCP port 5432 to Aurora PostgreSQL database subnets.
     - Blocks all other outbound internet traffic (mitigates reverse shell exfiltration).

#### Step 3: Apply NetworkPolicies
Execute:
```powershell
kubectl apply -f devops-configurations/kubernetes/networkpolicy.yaml
```

#### Step 4: Configure Least-Privilege RBAC for CI/CD Service Account
Create an isolated ServiceAccount and Role for CI/CD deployment runners instead of granting `cluster-admin`:
```yaml
# devops-configurations/kubernetes/cicd-rbac.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: fincore-cicd-deployer
  namespace: fincore
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: fincore-deployer-role
  namespace: fincore
rules:
  - apiGroups: ["apps"]
    resources: ["deployments", "statefulsets"]
    verbs: ["get", "list", "watch", "create", "update", "patch"]
  - apiGroups: [""]
    resources: ["services", "configmaps", "secrets"]
    verbs: ["get", "list", "watch", "create", "update", "patch"]
  - apiGroups: ["networking.k8s.io"]
    resources: ["ingresses", "networkpolicies"]
    verbs: ["get", "list", "watch", "create", "update", "patch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: fincore-deployer-binding
  namespace: fincore
subjects:
  - kind: ServiceAccount
    name: fincore-cicd-deployer
    namespace: fincore
roleRef:
  kind: Role
  name: fincore-deployer-role
  apiGroup: rbac.authorization.k8s.io
```
Apply the RBAC manifest:
```powershell
kubectl apply -f devops-configurations/kubernetes/cicd-rbac.yaml
```

### D. Configuration References
- NetworkPolicy Manifest: `devops-configurations/kubernetes/networkpolicy.yaml`
- RBAC Definition: `devops-configurations/kubernetes/cicd-rbac.yaml`
- Namespace Definition: `devops-configurations/kubernetes/namespace.yaml`

### E. Verification & Validation Steps
Verify that NetworkPolicies are active:
```powershell
kubectl get networkpolicies -n fincore
```
Test that backend pod CANNOT reach the external public internet (Zero-Trust verification):
```powershell
$BACKEND_POD = (kubectl get pods -n fincore -l app=fincore-backend -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n fincore $BACKEND_POD -- wget -qO- --timeout=3 http://www.google.com
```
*Expected Result:* Connection timed out / failed (Egress to public internet successfully blocked!).

Test that backend CAN reach the database and CoreDNS:
```powershell
kubectl exec -n fincore $BACKEND_POD -- nslookup fincore-backend.fincore.svc.cluster.local
```
*Expected Result:* Successfully returns internal ClusterIP address.

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Backend pod fails DNS lookups immediately after applying NetworkPolicy**
   - *Root Cause:* NetworkPolicy omitted egress permission for DNS traffic to CoreDNS.
   - *Remediation:* Ensure an explicit egress rule allows UDP and TCP port 53 targeting `namespaceSelector: matchLabels: { kubernetes.io/metadata.name: kube-system }`.
2. **Failure Mode: Prometheus cannot scrape `/actuator/prometheus`**
   - *Root Cause:* NetworkPolicy blocked ingress from the `monitoring` namespace.
   - *Remediation:* Add an ingress rule allowing traffic on port 8080 from pods labeled `app.kubernetes.io/name: prometheus` in namespace `monitoring`.
3. **Failure Mode: Pod rejected on deployment (`violates PodSecurity "restricted"`)**
   - *Root Cause:* The container spec did not specify `securityContext.runAsNonRoot: true` or `allowPrivilegeEscalation: false`.
   - *Remediation:* Align container securityContext with the strict non-root standards declared in `backend-deployment.yaml`.

### G. Rollback & Disaster Recovery Procedures
If a network policy blocks critical business traffic during an incident:
```powershell
# Temporarily remove network policy while diagnosing rule mismatches:
kubectl delete -f devops-configurations/kubernetes/networkpolicy.yaml
```

### H. Security & Compliance Guardrails
- In PCI-DSS Section 1.3, cardholder data environments must implement network segmentation that restricts traffic to only what is necessary for business functionality.
- Default deny-all ingress and egress rules are required for all production namespaces.

### I. Production vs Local Differences

| Dimension | Local Docker Network | Production EKS Cluster |
| :--- | :--- | :--- |
| **Network Filtering** | Open bridge networking between all containers | Hardware-enforced eBPF / iptables NetworkPolicy rules |
| **Privilege Model** | Docker root execution permitted locally | Kubernetes Restricted Pod Security Standards enforced |
| **Service RBAC** | None | Cryptographic JWT tokens bound to scoped ServiceAccounts |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why is a "Default Deny All" rule critical when implementing Kubernetes NetworkPolicies?**
  - *Answer:* In Kubernetes, if no NetworkPolicy selects a pod, the pod is considered "non-isolated" and accepts traffic from all sources. As soon as you apply an ingress rule, the pod becomes isolated, but only for ingress. By applying an explicit `default-deny-all` policy that isolates both ingress and egress, we ensure that every permitted communication path (e.g. backend to database, frontend to backend) must be explicitly whitelisted, guaranteeing a zero-trust network perimeter.
- **Q2: What is the difference between Kubernetes Role and ClusterRole?**
  - *Answer:* A `Role` defines permissions scoped strictly within a single namespace (e.g. creating deployments or reading configmaps in `fincore`). A `ClusterRole` defines non-namespaced permissions that apply across the entire cluster (e.g. inspecting nodes, managing storage classes, or reading secrets across all namespaces). In secure CI/CD pipelines, runners should be assigned a `Role` bound to their application namespace, never a `ClusterRole`.
- **Q3: How do you prevent egress data exfiltration attacks in containerized environments?**
  - *Answer:* We implement strict egress NetworkPolicies that deny all outbound traffic by default. We only whitelist egress to internal CoreDNS pods (port 53) and the database CIDR block (port 5432). Because outbound internet connections (ports 80, 443, 22) are blocked, even if an attacker achieves remote code execution inside a pod, they cannot download external malware payloads or establish reverse shell connections back to external command-and-control servers.

### K. Cost & Performance Considerations
- Kubernetes NetworkPolicies implemented via AWS VPC CNI or Calico execute directly in the Linux kernel via iptables or eBPF, adding sub-millisecond network latency overhead.

### L. Real-World Banking Considerations
- PCI-DSS Requirement 7 (Restrict Access to System Components and Cardholder Data by Business Need to Know) mandates least privilege access. RBAC roles ensure developers cannot modify production database secrets directly from Kubernetes CLI.

### M. Progress Sign-off Checklist
- [ ] Pod Security Standard `restricted` enforced on `fincore`.
- [ ] Zero-Trust NetworkPolicies applied.
- [ ] Egress to external internet verified blocked from backend pod.
- [ ] Internal DNS and DB connectivity verified functional.

### N. Next Manual Transition
Proceed to **[Manual 20: AWS Load Balancer Controller & ALB Ingress](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/20-aws-load-balancer-controller-ingress.md)** to expose banking services securely over HTTPS.

