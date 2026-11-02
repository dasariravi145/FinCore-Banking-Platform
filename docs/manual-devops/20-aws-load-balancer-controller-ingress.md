# FinCore Banking Platform — DevOps Engineering Manual
## Manual 20: AWS Load Balancer Controller, SSL/TLS Termination & Ingress Routing

### A. Objective & Architecture Context
In enterprise cloud environments, exposing Kubernetes services via `NodePort` or individual `LoadBalancer` types introduces security risks, excessive AWS billing (a separate AWS Network Load Balancer for each service), and lacks HTTP/2 or Layer 7 routing. This manual installs the AWS Load Balancer Controller (LBC) using IAM Roles for Service Accounts (IRSA) and configures an Application Load Balancer (ALB) Ingress using `devops-configurations/kubernetes/ingress.yaml` to provide TLS 1.3 encryption, automatic HTTP-to-HTTPS redirection, and path-based routing.

### B. Prerequisites & Dependencies
- Manual 15 completed (EKS cluster and OIDC Provider active).
- Helm 3.14+ installed on workstation.
- AWS Certificate Manager (ACM) TLS certificate generated or imported for your banking domain.

### C. Step-by-Step Implementation Instructions

#### Step 1: Create IAM Policy & Role for AWS Load Balancer Controller
Download the official AWS LBC IAM policy:
```powershell
curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.7.2/docs/install/iam_policy.json

aws iam create-policy `
  --policy-name AWSLoadBalancerControllerIAMPolicy `
  --policy-document file://iam_policy.json
```
Create the ServiceAccount with IRSA annotation using `eksctl` or AWS CLI:
```powershell
$CLUSTER_NAME = "fincore-cluster"
$ACCOUNT_ID = (aws sts get-caller-identity --query "Account" --output text)

eksctl create iamserviceaccount `
  --cluster=$CLUSTER_NAME `
  --namespace=kube-system `
  --name=aws-load-balancer-controller `
  --role-name "AmazonEKSLoadBalancerControllerRole" `
  --attach-policy-arn="arn:aws:iam::${ACCOUNT_ID}:policy/AWSLoadBalancerControllerIAMPolicy" `
  --approve
```

#### Step 2: Install AWS Load Balancer Controller via Helm
Add the EKS Helm chart repository and install:
```powershell
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm install aws-load-balancer-controller eks/aws-load-balancer-controller `
  -n kube-system `
  --set clusterName=$CLUSTER_NAME `
  --set serviceAccount.create=false `
  --set serviceAccount.name=aws-load-balancer-controller
```
Verify controller pods are running:
```powershell
kubectl get pods -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller
```

#### Step 3: Inspect FinCore Ingress Resource Specification
Inspect `devops-configurations/kubernetes/ingress.yaml`:
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: fincore-ingress
  namespace: fincore
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTP": 80}, {"HTTPS": 443}]'
    alb.ingress.kubernetes.io/ssl-redirect: '443'
    alb.ingress.kubernetes.io/certificate-arn: "REPLACE_WITH_YOUR_ACM_CERTIFICATE_ARN"
    alb.ingress.kubernetes.io/healthcheck-path: /actuator/health
spec:
  rules:
    - host: fincore.yourbank.com
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: fincore-backend
                port:
                  number: 8080
          - path: /
            pathType: Prefix
            backend:
              service:
                name: fincore-frontend
                port:
                  number: 80
```

#### Step 4: Deploy Ingress Resource
Update `ingress.yaml` with your ACM certificate ARN and domain, then apply:
```powershell
kubectl apply -f devops-configurations/kubernetes/ingress.yaml
```

### D. Configuration References
- Ingress Manifest: `devops-configurations/kubernetes/ingress.yaml`
- Backend Service: `devops-configurations/kubernetes/backend-service.yaml`
- Frontend Service: `devops-configurations/kubernetes/frontend-service.yaml`

### E. Verification & Validation Steps
Wait 2 to 3 minutes for the AWS Load Balancer Controller to provision the AWS ALB.
Inspect Ingress address:
```powershell
kubectl get ingress fincore-ingress -n fincore
```
*Expected Output:* An active AWS ALB DNS address (e.g. `k8s-fincore-xxxxxx.us-east-1.elb.amazonaws.com`).

Verify HTTP to HTTPS redirection:
```powershell
curl -I http://<ALB-DNS-NAME>
```
*Expected Response:* `HTTP/1.1 301 Moved Permanently` -> `Location: https://...:443/`

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Ingress address remains empty indefinitely**
   - *Root Cause:* AWS LBC cannot discover public subnets or lacks IAM permissions.
   - *Remediation:* Check controller logs:
     ```powershell
     kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller --tail=50
     ```
     Ensure public subnets possess tag `kubernetes.io/role/elb = 1`.
2. **Failure Mode: Target group health checks return `502 Bad Gateway` or `Unhealthy`**
   - *Root Cause:* ALB attempted to health-check `/` on the backend instead of `/actuator/health`.
   - *Remediation:* Specify `alb.ingress.kubernetes.io/healthcheck-path: /actuator/health` in the backend service or Ingress annotations.
3. **Failure Mode: `target-type: ip` fails to register pods**
   - *Root Cause:* Security group on worker nodes blocks ingress traffic originating from the ALB.
   - *Remediation:* Verify that node security groups permit incoming traffic on ports 80 and 8080 from the ALB security group.

### G. Rollback & Disaster Recovery Procedures
To roll back or delete the ALB Ingress without touching backend pods:
```powershell
kubectl delete ingress fincore-ingress -n fincore
```
The AWS Load Balancer Controller will automatically deprovision the AWS ALB and release associated Elastic IPs.

### H. Security & Compliance Guardrails
- **TLS 1.3 Enforced:** Configure ALB SSL policy to `ELBSecurityPolicy-TLS13-1-2-2021-06` to disable legacy, vulnerable TLS 1.0 and 1.1 ciphers.
- **WAF Integration:** Attach AWS WAF to the ALB via annotation `alb.ingress.kubernetes.io/wafv2-acl-arn` to block SQL injection and cross-site scripting attacks at the edge.

### I. Production vs Local Differences

| Dimension | Local Docker Setup | Production AWS ALB Ingress |
| :--- | :--- | :--- |
| **Routing Layer** | Local Nginx container reverse proxy on port 80 | Managed multi-AZ AWS Application Load Balancer |
| **Target Mode** | Docker bridge IP translation | Direct Pod IP target routing (`target-type: ip`) via VPC CNI |
| **TLS Offloading** | Unencrypted HTTP | Hardware TLS offloading with auto-renewing ACM certificates |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why is `target-type: ip` preferred over `target-type: instance` in EKS?**
  - *Answer:* In `instance` mode, the ALB routes traffic to an EC2 worker node's `NodePort`, which then relies on `kube-proxy` iptables to route the packet to the actual pod, potentially incurring an extra cross-node network hop. In `ip` mode (enabled by the AWS VPC CNI where every pod receives a native VPC IP), the ALB routes packets directly to the pod's ENI IP address, eliminating the `kube-proxy` hop, reducing latency, and enabling accurate client IP preservation.
- **Q2: How does the AWS Load Balancer Controller differ from the legacy in-tree Kubernetes cloud provider?**
  - *Answer:* The legacy in-tree controller provisioned classic ELBs (CLB) or basic NLBs per Service, lacking support for modern ALB features like path-based routing, weighted target groups, WAF integration, and SSL redirection. The out-of-tree AWS Load Balancer Controller manages ALBs and NLBs natively via Kubernetes Custom Resource Definitions (CRDs) and Ingress specifications, delivering full AWS cloud-native capability.
- **Q3: How do you handle zero-downtime rolling deployments when using an ALB Ingress?**
  - *Answer:* When a pod terminates during a rollout, there is a delay before the ALB target group registers the pod as deregistered. We configure `preStop` lifecycle hooks with a sleep period (e.g. `sleep 15`) and set the target group deregistration delay to 30s. This ensures the pod continues processing active banking transactions in flight while the ALB stops forwarding new traffic.

### K. Cost & Performance Considerations
- AWS Application Load Balancer costs ~$0.0225/hour (~$16.20/month) plus $0.008 per Load Balancer Capacity Unit (LCU).
- Consolidating frontend and backend routing into a single ALB cuts load balancer costs in half.

### L. Real-World Banking Considerations
- PCI-DSS Requirement 4 (Protect Cardholder Data with Strong Cryptography During Transmission Over Open Networks) mandates TLS 1.2+ for all public-facing financial interfaces. Terminating TLS at the ALB with strict ciphers ensures end-to-end cryptographic compliance.

### M. Progress Sign-off Checklist
- [ ] AWS Load Balancer Controller installed and healthy in `kube-system`.
- [ ] Ingress resource deployed in `fincore` namespace.
- [ ] AWS ALB provisioned and DNS hostname assigned.
- [ ] HTTP to HTTPS redirect verified functional.

### N. Next Manual Transition
Proceed to **[Manual 21: Helm Packaging, Templating & Values Strategy](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/21-helm-packaging-values-environments.md)** to package raw Kubernetes manifests into parameterizable, version-controlled Helm charts.

