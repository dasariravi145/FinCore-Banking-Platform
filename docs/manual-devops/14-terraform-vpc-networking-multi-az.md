# FinCore Banking Platform — DevOps Engineering Manual
## Manual 14: Terraform Multi-AZ VPC Networking & Subnet Architecture

### A. Objective & Architecture Context
A defense-in-depth banking infrastructure requires a strict, multi-tier network topology. Placing application servers or databases directly in public subnets exposes them to internet-based scanning and DDoS attacks. This manual provisions an enterprise 3-tier Multi-AZ Virtual Private Cloud (VPC) across 3 Availability Zones using `devops-configurations/terraform/vpc.tf`, implementing public, private application, and isolated database subnets with mandatory Kubernetes Load Balancer tags.

### B. Prerequisites & Dependencies
- Manual 13 completed (AWS CLI configured, S3/DynamoDB remote state ready).
- Terraform CLI 1.8+ installed.
- 3 distinct Availability Zones available in target AWS Region (e.g. `us-east-1a`, `us-east-1b`, `us-east-1c`).

### C. Step-by-Step Implementation Instructions

#### Step 1: Inspect VPC Subnet Architecture & CIDR Plan
Review `devops-configurations/terraform/vpc.tf`:
- **VPC CIDR:** `10.0.0.0/16` (65,536 total private IPs)
- **Tier 1: Public Subnets (3x /24):**
  - Subnets: `10.0.1.0/24`, `10.0.2.0/24`, `10.0.3.0/24`
  - Purpose: Internet-facing Application Load Balancers (ALB) and NAT Gateways.
  - Tag: `kubernetes.io/role/elb = 1`
- **Tier 2: Private Application Subnets (3x /20):**
  - Subnets: `10.0.16.0/20`, `10.0.32.0/20`, `10.0.48.0/20`
  - Purpose: EKS worker nodes, Spring Boot pods, Nginx pods. Outbound internet routed strictly through NAT Gateway.
  - Tag: `kubernetes.io/role/internal-elb = 1`
- **Tier 3: Isolated Database Subnets (3x /24):**
  - Subnets: `10.0.64.0/24`, `10.0.65.0/24`, `10.0.66.0/24`
  - Purpose: Aurora PostgreSQL cluster. Zero internet routing (no IGW, no NAT Gateway).

#### Step 2: Initialize Terraform Backend
Navigate to `devops-configurations/terraform/`:
```powershell
cd D:\Projects\FinCore-Banking-Platform\devops-configurations\terraform
terraform init
```
Verify that Terraform successfully connects to your remote S3 backend and DynamoDB lock table.

#### Step 3: Execute Terraform Plan for VPC
Generate an execution plan targeting network resources:
```powershell
terraform plan -target=aws_vpc.fincore_vpc -out=vpc.tfplan
```
Review the proposed changes: 1 VPC, 9 Subnets, 1 Internet Gateway, 1 NAT Gateway, Route Tables, and Associations.

#### Step 4: Apply VPC Configuration
Execute the approved plan:
```powershell
terraform apply vpc.tfplan
```
*Wait Time:* Approximately 2 to 3 minutes for NAT Gateway allocation.

### D. Configuration References
- VPC Resource Definitions: `devops-configurations/terraform/vpc.tf`
- Variables & CIDR Blocks: `devops-configurations/terraform/variables.tf`
- Outputs: `devops-configurations/terraform/outputs.tf`

### E. Verification & Validation Steps
Verify subnet creation and tagging via AWS CLI:
```powershell
aws ec2 describe-subnets `
  --filters "Name=vpc-id,Values=$(terraform output -raw vpc_id)" `
  --query "Subnets[*].[SubnetId,CidrBlock,AvailabilityZone,Tags[?Key=='Name'].Value|[0]]" `
  --output table
```
Verify Kubernetes Load Balancer tags:
```powershell
aws ec2 describe-subnets `
  --filters "Name=tag:kubernetes.io/role/elb,Values=1" `
  --query "Subnets[*].SubnetId" --output text
```

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `InsufficientFreeAddressesInSubnet` during pod scale-up**
   - *Root Cause:* Private subnets were carved too small (e.g. `/24` providing only 251 usable IPs), exhausted by AWS VPC CNI allocating secondary IP addresses per pod.
   - *Remediation:* FinCore's CIDR plan allocates `/20` subnets (4,091 usable IPs each) for private worker tiers to accommodate heavy pod scaling.
2. **Failure Mode: AWS Load Balancer Controller cannot discover subnets for Ingress**
   - *Root Cause:* Subnets lack the mandatory `kubernetes.io/role/elb = 1` or cluster discovery tags.
   - *Remediation:* Ensure tags `kubernetes.io/role/elb = 1` (public) and `kubernetes.io/role/internal-elb = 1` (private) are present in `vpc.tf`.
3. **Failure Mode: High AWS bill due to NAT Gateways**
   - *Root Cause:* Provisioning 3 NAT Gateways (one per AZ) costs ~$97/month in idle charges alone.
   - *Remediation:* For dev/staging environments, set variable `single_nat_gateway = true` in `variables.tf` to route all private subnet traffic through a single NAT Gateway.

### G. Rollback & Disaster Recovery Procedures
To safely destroy VPC networking without leaving orphaned elastic network interfaces (ENIs):
1. Ensure all EKS clusters and RDS databases attached to the VPC are destroyed first.
2. Run `terraform destroy -target=aws_vpc.fincore_vpc`.

### H. Security & Compliance Guardrails
- **No Direct Internet Routing for DB:** Database subnets have no route to the Internet Gateway or NAT Gateway. Database packets cannot leave the VPC, mitigating external data exfiltration risks.
- **VPC Flow Logs:** Enable VPC Flow Logs with CloudWatch or S3 delivery to capture all accepted and rejected IP traffic flows for SOC 2 compliance.

### I. Production vs Local Differences

| Dimension | Local Docker Network | Production AWS VPC |
| :--- | :--- | :--- |
| **Topology** | Flat software bridge (`fincore-network`) | 3-tier Multi-AZ segmented routing with security groups |
| **Isolation** | Software firewall / iptables rules | Hardware-enforced AWS hypervisor VPC boundaries |
| **Egress** | Host machine network card directly | Dedicated AWS NAT Gateways with static Elastic IPs |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why are database subnets isolated from the NAT Gateway in banking architectures?**
  - *Answer:* Database engines (PostgreSQL) require inbound connections from authorized backend application servers to process transactions, but never need to initiate outbound connections to the public internet. By omitting default 0.0.0.0/0 routes to both IGWs and NAT Gateways, we create an air-gapped subnet tier where database compromises cannot establish outbound command-and-control (C2) channels or exfiltrate customer financial records.
- **Q2: Why are specific tags (`kubernetes.io/role/elb`) required on public subnets?**
  - *Answer:* When the AWS Load Balancer Controller processes an Ingress resource with `alb.ingress.kubernetes.io/scheme: internet-facing`, it queries AWS EC2 APIs to discover where to place the ALB. It identifies eligible subnets by searching for the tag `kubernetes.io/role/elb = 1`. If this tag is missing, ALB provisioning fails with subnet discovery errors.
- **Q3: What is the cost optimization strategy for NAT Gateways between staging and production?**
  - *Answer:* In production, high availability demands a dedicated NAT Gateway per AZ so that an AZ failure does not disrupt egress traffic for surviving nodes. In development and staging, where 99.99% multi-AZ SLA is not required, we use a single NAT Gateway shared across all private subnets (`single_nat_gateway = true`), immediately cutting monthly AWS networking spend by 66%.

### K. Cost & Performance Considerations
- Single NAT Gateway costs ~$0.045/hour (~$32.40/month) plus $0.045 per GB data processed.
- Intra-VPC traffic within the same AZ is free; inter-AZ traffic incurs $0.01/GB.

### L. Real-World Banking Considerations
- Financial institutions must comply with PCI-DSS Requirement 1 (Install and Maintain Network Security Controls). The 3-tier architecture with stateful Security Groups and stateless Network ACLs satisfies regulatory perimeter security controls.

### M. Progress Sign-off Checklist
- [ ] `terraform init` successful with remote state.
- [ ] VPC provisioned across 3 Availability Zones.
- [ ] 9 Subnets created (3 public, 3 private, 3 database).
- [ ] Kubernetes ALB tags verified.

### N. Next Manual Transition
Proceed to **[Manual 15: Terraform EKS Cluster & Managed Node Groups](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/15-terraform-eks-cluster-managed-node-groups.md)** to provision the managed Kubernetes control plane and worker compute nodes.

