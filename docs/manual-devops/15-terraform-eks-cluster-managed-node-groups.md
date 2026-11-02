# FinCore Banking Platform — DevOps Engineering Manual
## Manual 15: Terraform Amazon EKS Cluster & Managed Node Groups

### A. Objective & Architecture Context
FinCore's containerized microservices require an enterprise-grade container orchestration platform that provides high availability, automated pod rescheduling, rolling zero-downtime updates, and strict IAM integration. This manual provisions an Amazon Elastic Kubernetes Service (EKS) 1.30 cluster with AWS-managed node groups running inside private application subnets using `devops-configurations/terraform/eks.tf` and `iam.tf`.

### B. Prerequisites & Dependencies
- Manual 14 completed (VPC and subnets active).
- `kubectl` 1.30+ installed on workstation.
- AWS CLI configured with permissions to create IAM roles, EKS clusters, and Auto Scaling groups.

### C. Step-by-Step Implementation Instructions

#### Step 1: Review IAM Roles and Security Policies
Inspect `devops-configurations/terraform/iam.tf`:
- **Cluster Role (`aws_iam_role.eks_cluster_role`):** Attached with `AmazonEKSClusterPolicy`. Allows AWS to manage ENIs and load balancers on behalf of the Kubernetes control plane.
- **Node Role (`aws_iam_role.eks_nodes_role`):** Attached with:
  - `AmazonEKSWorkerNodePolicy` (Worker node daemon communication)
  - `AmazonEKS_CNI_Policy` (AWS VPC CNI IP allocation)
  - `AmazonEC2ContainerRegistryReadOnly` (Pulling images from ECR)
- **IAM OIDC Provider:** Created via `aws_iam_openid_connect_provider` to enable IAM Roles for Service Accounts (IRSA).

#### Step 2: Review EKS Cluster & Node Group Resource Specifications
Inspect `devops-configurations/terraform/eks.tf`:
- **Kubernetes Version:** `1.30`
- **Control Plane Logging:** Enabled for `api`, `audit`, `authenticator`, `controllerManager`, `scheduler`.
- **Private Endpoint:** Control plane API accessible via private subnets and restricted public CIDRs.
- **Managed Node Group (`fincore-nodes`):**
  - Subnets: Private application subnets (`aws_subnet.private[*].id`)
  - Instance Types: `t3.medium` (Staging) or `m6i.large` / Graviton `m7g.large` (Production)
  - Scaling Configuration: `desired_size = 2`, `min_size = 2`, `max_size = 5`
  - AMI Type: `AL2023_x86_64_STANDARD` or `AL2023_ARM_64_STANDARD`
  - Capacity Type: `ON_DEMAND` for banking core reliability.

#### Step 3: Execute Terraform Plan for EKS
Generate and inspect the EKS execution plan:
```powershell
terraform plan -target=aws_eks_cluster.fincore_eks -out=eks.tfplan
```
Review the proposed resources (Cluster, Node Group, IAM Roles, OIDC Provider, Security Groups).

#### Step 4: Apply EKS Infrastructure
Apply the configuration:
```powershell
terraform apply eks.tfplan
```
*Wait Time:* Approximately 10 to 15 minutes for AWS EKS control plane provisioning and node bootstrapping.

#### Step 5: Update Local `kubeconfig`
Configure your local workstation `kubectl` context to authenticate with the new cluster:
```powershell
$CLUSTER_NAME = terraform output -raw eks_cluster_name
$REGION = "us-east-1"

aws eks update-kubeconfig --name $CLUSTER_NAME --region $REGION
```

### D. Configuration References
- EKS Cluster Manifest: `devops-configurations/terraform/eks.tf`
- IAM Roles & OIDC: `devops-configurations/terraform/iam.tf`
- Outputs: `devops-configurations/terraform/outputs.tf`

### E. Verification & Validation Steps
Verify node cluster health via `kubectl`:
```powershell
kubectl get nodes -o wide
```
*Expected Output:*
```
NAME                          STATUS   ROLES    AGE   VERSION   INTERNAL-IP
ip-10-0-16-45.ec2.internal    Ready    <none>   3m    v1.30.x   10.0.16.45
ip-10-0-32-112.ec2.internal   Ready    <none>   3m    v1.30.x   10.0.32.112
```
Verify system daemon pods in `kube-system`:
```powershell
kubectl get pods -n kube-system
```
Confirm `aws-node`, `coredns`, and `kube-proxy` pods report `Running`.

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `kubectl` commands return `error: You must be logged in to the server (Unauthorized)`**
   - *Root Cause:* The IAM identity used to run `kubectl` is different from the IAM entity that executed `terraform apply` to create the cluster.
   - *Remediation:* EKS assigns initial `system:masters` RBAC permissions exclusively to the creator IAM entity. Add your current IAM user ARN to the EKS Access Entries (`aws eks create-access-entry`).
2. **Failure Mode: Nodes fail to join cluster (`kubectl get nodes` returns empty)**
   - *Root Cause:* Private subnets lack a working NAT Gateway or route to reach the EKS control plane endpoint.
   - *Remediation:* Verify that private subnet route tables have `0.0.0.0/0` pointing to the NAT Gateway in `vpc.tf`.
3. **Failure Mode: `CoreDNS` pods remain in `Pending` state indefinitely**
   - *Root Cause:* Node security group blocks UDP port 53 or AWS VPC CNI failed to assign secondary IPs.
   - *Remediation:* Ensure security group rules allow intra-cluster traffic on ports 53 and 443.

### G. Rollback & Disaster Recovery Procedures
To cleanly teardown EKS compute resources:
```powershell
terraform destroy -target=aws_eks_node_group.fincore_node_group
terraform destroy -target=aws_eks_cluster.fincore_eks
```

### H. Security & Compliance Guardrails
- **Nodes in Private Subnets Only:** Worker nodes possess zero public IPv4 addresses; they communicate with external services exclusively through NAT Gateways.
- **Audit Logging Enabled:** Control plane audit logs stream directly to CloudWatch Logs with 90-day retention for regulatory compliance.
- **IMDSv2 Enforced:** EC2 instance metadata service is locked to IMDSv2 (`http_tokens = "required"`) with hop limit of 1 to prevent SSRF credential theft.

### I. Production vs Local Differences

| Dimension | Local Docker Compose | Amazon EKS Cluster |
| :--- | :--- | :--- |
| **Control Plane** | Docker daemon running on Windows | Managed, multi-AZ Kubernetes API servers backed by AWS |
| **Compute Scaling** | Fixed host hardware | Auto Scaling Group dynamically adjusting EC2 instances |
| **Identity** | Local root/user inside container | AWS IAM OIDC integration (IRSA) with temporary STS tokens |

### J. Interview Readiness Questions & Model Answers
- **Q1: What is IRSA (IAM Roles for Service Accounts) and why is it superior to node-level IAM roles?**
  - *Answer:* Node-level IAM roles grant permissions to the entire EC2 instance, meaning any pod running on that node inherits all permissions. In a multi-tenant banking cluster, a compromised frontend pod could steal database credentials. IRSA associates an AWS IAM role directly with a Kubernetes ServiceAccount using OIDC federated tokens. The AWS STS service issues temporary, scoped credentials mounted directly inside the specific pod, achieving true least-privilege security.
- **Q2: Why are EKS worker nodes deployed strictly into private subnets?**
  - *Answer:* Deploying worker nodes into public subnets assigns public IP addresses to EC2 instances, exposing the Kubelet port (10250), container runtimes, and application ports to internet port scanners. In private subnets, nodes have no public IP, cannot receive unsolicited inbound internet packets, and only expose approved traffic through an authenticated Application Load Balancer.
- **Q3: What happens during an EKS managed node group version upgrade?**
  - *Answer:* EKS executes a rolling update. It provisions a new EC2 instance with the updated AMI, cordons and drains pods from an old node respecting Pod Disruption Budgets (PDBs), waits for pods to be ready on the new node, and terminates the old instance. This guarantees zero application downtime during OS kernel and Kubernetes version patches.

### K. Cost & Performance Considerations
- EKS Control Plane fee is fixed at $0.10/hour ($72.00/month).
- 2x `t3.medium` instances cost ~$0.0416/hour each (~$60.00/month combined).
- Total baseline EKS infrastructure cost: ~$132.00/month. Always destroy or scale down test clusters when not in use.

### L. Real-World Banking Considerations
- Financial institutions must comply with PCI-DSS Requirement 10 (Track and Monitor All Access). Enabling EKS Control Plane Audit Logs captures every Kubernetes API call (who, when, what namespace) for forensic auditing.

### M. Progress Sign-off Checklist
- [ ] EKS 1.30 cluster provisioned in active state.
- [ ] IAM OIDC provider registered.
- [ ] Managed Node Group nodes reporting `Ready`.
- [ ] `kubectl get nodes` verified from local workstation.

### N. Next Manual Transition
Proceed to **[Manual 16: Terraform Amazon RDS Aurora PostgreSQL](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/16-terraform-rds-aurora-postgresql.md)** to provision the enterprise relational database layer.

