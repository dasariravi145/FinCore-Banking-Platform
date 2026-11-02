# FinCore Banking Platform — DevOps Engineering Manual
## Manual 18: Kubernetes Core Manifests, Workload Architecture & Probes

### A. Objective & Architecture Context
Transitioning FinCore from Docker Compose to Amazon EKS requires translating container definitions into cloud-native Kubernetes primitives. In enterprise banking, application pods must be resilient to node failures, auto-heal from deadlocks, dynamically scale under sudden transaction loads, and guarantee zero-downtime rolling updates. This manual implements the core Kubernetes workloads located in `devops-configurations/kubernetes/`.

### B. Prerequisites & Dependencies
- Manual 15 completed (EKS cluster and managed node groups active; `kubectl get nodes` returns Ready).
- Aurora PostgreSQL cluster provisioned (Manual 16) or local PostgreSQL instance accessible.
- `kubectl` 1.30+ configured with cluster context.

### C. Step-by-Step Implementation Instructions

#### Step 1: Create Dedicated Banking Namespace
Deploy `devops-configurations/kubernetes/namespace.yaml`:
```powershell
kubectl apply -f devops-configurations/kubernetes/namespace.yaml
```
Verify namespace creation:
```powershell
kubectl get namespace fincore
```

#### Step 2: Deploy ConfigMaps and Secrets Template
Deploy non-sensitive configuration:
```powershell
kubectl apply -f devops-configurations/kubernetes/configmaps.yaml
```
Inspect `devops-configurations/kubernetes/secrets-template.yaml`.
Generate a real Kubernetes Secret for staging:
```powershell
kubectl create secret generic fincore-backend-secrets `
  --namespace=fincore `
  --from-literal=DB_PASSWORD="your-strong-aurora-db-password" `
  --from-literal=JWT_SECRET="fincore-super-secret-jwt-key-minimum-32-bytes-long!" `
  --dry-run=client -o yaml | kubectl apply -f -
```

#### Step 3: Deploy Backend Workload & Service
Review `devops-configurations/kubernetes/backend-deployment.yaml`:
- **Replicas:** 2 (guaranteeing multi-AZ redundancy).
- **Security Context:** `runAsNonRoot: true`, `runAsUser: 10001`, `allowPrivilegeEscalation: false`.
- **Resource Requests & Limits:**
  - Requests: CPU `250m`, Memory `512Mi`.
  - Limits: CPU `1000m`, Memory `1024Mi`.
- **Health Probes:**
  - `startupProbe`: `/actuator/health/liveness`, initial delay 15s, period 5s, failureThreshold 30 (gives JVM 2.5 minutes to warm up and run Flyway before Kubernetes restarts it).
  - `livenessProbe`: `/actuator/health/liveness`, period 15s, timeout 3s.
  - `readinessProbe`: `/actuator/health/readiness`, period 10s, timeout 3s.
- **Pod Anti-Affinity:** Prevents both backend replicas from scheduling on the same physical EC2 worker node.

Deploy backend:
```powershell
kubectl apply -f devops-configurations/kubernetes/backend-deployment.yaml
kubectl apply -f devops-configurations/kubernetes/backend-service.yaml
```

#### Step 4: Deploy Frontend Workload & Service
Deploy `devops-configurations/kubernetes/frontend-deployment.yaml` and `frontend-service.yaml`:
```powershell
kubectl apply -f devops-configurations/kubernetes/frontend-deployment.yaml
kubectl apply -f devops-configurations/kubernetes/frontend-service.yaml
```

#### Step 5: Deploy High-Availability Guardrails (HPA & PDB)
Deploy Horizontal Pod Autoscaler and Pod Disruption Budget:
```powershell
kubectl apply -f devops-configurations/kubernetes/hpa.yaml
kubectl apply -f devops-configurations/kubernetes/pdb.yaml
```

### D. Configuration References
- Manifests directory: `devops-configurations/kubernetes/`
- Backend Deployment: `backend-deployment.yaml`
- Frontend Deployment: `frontend-deployment.yaml`
- Services: `backend-service.yaml`, `frontend-service.yaml`
- Auto-scaling & Disruption: `hpa.yaml`, `pdb.yaml`

### E. Verification & Validation Steps
Verify that all pods in `fincore` namespace transition to `Running` and report `1/1 Ready`:
```powershell
kubectl get pods -n fincore -o wide
```
Verify Services and ClusterIP allocations:
```powershell
kubectl get svc -n fincore
```
Verify Pod Disruption Budget:
```powershell
kubectl get pdb -n fincore
```
*Expected Output:* `ALLOWED DISRUPTIONS: 1`

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Backend pod killed after 30 seconds (`CrashLoopBackOff`)**
   - *Root Cause:* Kubernetes killed the container because `livenessProbe` failed before Spring Boot completed Flyway migrations.
   - *Remediation:* FinCore implements a dedicated `startupProbe`. The startup probe must succeed before the liveness probe begins evaluating. Increase `startupProbe.failureThreshold` if migrations take longer.
2. **Failure Mode: `OOMKilled` (Exit code 137)**
   - *Root Cause:* JVM heap plus off-heap memory exceeded container `limits.memory: 1024Mi`.
   - *Remediation:* Ensure `-XX:MaxRAMPercentage=75.0` is set in the ConfigMap JVM options so heap caps at 768MB, leaving 256MB for Metaspace and thread stacks.
3. **Failure Mode: Pods stuck in `Pending` state**
   - *Root Cause:* Insufficient worker node CPU/memory or `podAntiAffinity` rules cannot be satisfied.
   - *Remediation:* Run `kubectl describe pod <pod-name> -n fincore` to view scheduling events. If node capacity is exhausted, increase EKS Node Group `desired_size`.

### G. Rollback & Disaster Recovery Procedures
To roll back a bad deployment revision in Kubernetes:
```powershell
kubectl rollout undo deployment/fincore-backend -n fincore
kubectl rollout status deployment/fincore-backend -n fincore
```

### H. Security & Compliance Guardrails
- **Read-Only Root Filesystem:** Where possible, container root filesystems are mounted read-only, with `/tmp` mounted via an ephemeral `emptyDir` volume.
- **Linux Capabilities Dropped:** `capabilities.drop: ["ALL"]` prevents container processes from modifying kernel networking or mounting devices.
- **Resource Limits Enforced:** Every pod defines explicit CPU and Memory limits to prevent noisy-neighbor memory starvation.

### I. Production vs Local Differences

| Dimension | Local Docker Compose | Amazon EKS Kubernetes |
| :--- | :--- | :--- |
| **Failover Unit** | Single host process restart | Pod rescheduling across independent EC2 instances |
| **Autoscaling** | Manual `docker compose scale` | Dynamic HPA based on CPU, Memory, and Prometheus metrics |
| **Availability Policy** | None (host restart brings down all) | PodDisruptionBudget ensuring minimum available pods during upgrades |

### J. Interview Readiness Questions & Model Answers
- **Q1: What is the architectural difference between a Liveness Probe, a Readiness Probe, and a Startup Probe?**
  - *Answer:* A **Startup Probe** determines if the application has completed initialization (e.g. JVM warmup and database migrations); while it is running, liveness and readiness probes are disabled. A **Readiness Probe** determines if the pod is ready to accept incoming customer network traffic; if it fails, Kubernetes removes the pod's IP from Service endpoints without restarting it. A **Liveness Probe** determines if the process is deadlocked or permanently hung; if it fails, Kubernetes immediately terminates the container and restarts it.
- **Q2: Why are Pod Disruption Budgets (PDB) critical in financial systems?**
  - *Answer:* During automated Kubernetes node draining (e.g., node upgrades or cluster autoscaler scale-down), Kubernetes evicts pods. Without a PDB, Kubernetes could evict all backend replicas simultaneously, causing a temporary banking outage. A PDB (`minAvailable: 1`) guarantees that at least one replica is always healthy and serving traffic before the control plane permits node drain operations to proceed.
- **Q3: How does Pod Anti-Affinity enhance high availability?**
  - *Answer:* By default, Kubernetes scheduling algorithms may place multiple replicas of a deployment on the same physical EC2 worker node. If that single EC2 instance suffers a hardware failure, all backend replicas die simultaneously. Pod Anti-Affinity (`topologyKey: kubernetes.io/hostname` or `topologyKey: topology.kubernetes.io/zone`) forces Kubernetes to schedule replicas across different nodes and different Availability Zones.

### K. Cost & Performance Considerations
- Memory requests of `512Mi` per pod ensure predictable bin-packing on EC2 worker nodes.
- Using 2 replicas consumes 1 vCPU and 1 GB RAM total baseline for backend compute.

### L. Real-World Banking Considerations
- Financial transactions in flight must not be abruptly severed. Configuring Spring Boot's graceful shutdown (`server.shutdown=graceful`) in tandem with Kubernetes `terminationGracePeriodSeconds: 45` allows existing transactions to commit cleanly before the pod receives SIGKILL.

### M. Progress Sign-off Checklist
- [ ] Namespace `fincore` created.
- [ ] ConfigMaps and Secrets applied.
- [ ] Backend and Frontend deployments reporting 1/1 Ready.
- [ ] HPA and PDB active and verified.

### N. Next Manual Transition
Proceed to **[Manual 19: Kubernetes Security, RBAC & NetworkPolicies](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/19-kubernetes-security-rbac-networkpolicies.md)** to enforce zero-trust network boundaries and role-based access controls.

