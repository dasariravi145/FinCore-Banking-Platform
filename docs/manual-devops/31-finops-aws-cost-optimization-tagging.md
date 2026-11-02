# FinCore Banking Platform — DevOps Engineering Manual
## Manual 31: FinOps AWS Cost Optimization, Tagging & Infrastructure Right-Sizing

### A. Objective & Architecture Context
Unmanaged cloud infrastructure quickly leads to runaway monthly spending. In an enterprise banking environment, platform engineers must practice continuous Financial Operations (FinOps): ensuring every dollar spent delivers maximum architectural value. This manual details the cost optimization strategies, resource allocation tagging, right-sizing models, and AWS billing guardrails implemented in FinCore's Terraform configurations.

### B. Prerequisites & Dependencies
- Manuals 13–16 completed (AWS resources managed via Terraform).
- Access to AWS Cost Explorer and AWS Budgets in the AWS Management Console.
- Terraform input variables in `devops-configurations/terraform/variables.tf`.

### C. Step-by-Step Implementation Instructions

#### Step 1: Implement Mandatory Cost Allocation Tagging
Every AWS resource declared in Terraform must include consistent metadata tags:
Review `devops-configurations/terraform/versions.tf` (provider default tags):
```hcl
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "FinCore-Banking-Platform"
      Environment = var.environment
      ManagedBy   = "Terraform"
      CostCenter  = "Core-Banking-Engineering"
      Compliance  = "PCI-DSS-Tier1"
    }
  }
}
```
In AWS Billing Console -> **Cost Allocation Tags**, activate these user-defined tags to enable granular cost reporting per microservice.

#### Step 2: Implement High-Impact Architecture Cost Optimizations

##### Optimization 1: Single NAT Gateway in Non-Production Environments
- **The Issue:** Provisioning 3 NAT Gateways across 3 AZs costs ~$97.20/month base cost before data charges.
- **The Solution:** In `devops-configurations/terraform/variables.tf`, set `single_nat_gateway = true` for dev/staging. All private subnets share a single NAT Gateway, immediately reducing monthly NAT spend by 66% (~$64.80/month savings).

##### Optimization 2: AWS Graviton (ARM64) Migration for EKS Worker Nodes
- **The Issue:** x86 instances (`m6i.large`) cost ~$0.096/hour.
- **The Solution:** Switch EKS node groups to AWS Graviton 3 (`m7g.large` at ~$0.0816/hour). Graviton provides ~19% lower hourly compute cost and up to 40% better price-performance for Java 21 workloads.
- Set `ami_type = "AL2023_ARM_64_STANDARD"` and `instance_types = ["m7g.large"]` in `eks.tf`.

##### Optimization 3: Aurora Serverless v2 Right-Sizing
- **The Issue:** Provisioned multi-AZ DB instances run at fixed, high cost 24/7 regardless of traffic.
- **The Solution:** In `devops-configurations/terraform/rds.tf`, set `min_capacity = 0.5` ACUs (1 GB RAM). At night and during low activity, the database scales down to 0.5 ACU (~$0.06/hour), scaling up automatically to 8 ACUs only during peak transaction windows.

##### Optimization 4: Amazon S3 Lifecycle Rules for Logs and State Backups
- Automatically transition Loki log chunks and Terraform state versions to **S3 Glacier Instant Retrieval** after 30 days, cutting storage costs by ~68% ($0.023/GB -> $0.004/GB).

#### Step 3: Configure AWS Hard Budget & Anomaly Detection Alerts
Execute in PowerShell using AWS CLI:
```powershell
# Create AWS Budget at $30.00 monthly limit with 80% threshold alert
$ACCOUNT_ID = (aws sts get-caller-identity --query "Account" --output text)

aws budgets create-budget `
  --account-id $ACCOUNT_ID `
  --budget '{
    "BudgetName": "FinCore-Monthly-Safety-Budget",
    "BudgetLimit": {
      "Amount": "30",
      "Unit": "USD"
    },
    "CostTypes": {
      "IncludeTax": true,
      "IncludeSubscription": true,
      "UseBlended": false
    },
    "TimeUnit": "MONTHLY",
    "BudgetType": "COST"
  }' `
  --notifications-with-subscribers '[{
    "Notification": {
      "NotificationType": "ACTUAL",
      "ComparisonOperator": "GREATER_THAN",
      "Threshold": 80,
      "ThresholdType": "PERCENTAGE"
    },
    "Subscribers": [{
      "SubscriptionType": "EMAIL",
      "Address": "your-email@example.com"
    }]
  }]'
```

### D. Configuration References
- Terraform Variables & Cost Toggles: `devops-configurations/terraform/variables.tf`
- Default Provider Tags: `devops-configurations/terraform/versions.tf`
- RDS Autoscaling Limits: `devops-configurations/terraform/rds.tf`

### E. Verification & Validation Steps
Verify that active AWS resources carry the required FinOps tags:
```powershell
aws ec2 describe-instances `
  --filters "Name=tag:Project,Values=FinCore-Banking-Platform" `
  --query "Reservations[*].Instances[*].[InstanceId,InstanceType,Tags[?Key=='Environment'].Value|[0]]" `
  --output table
```
Inspect AWS Cost Explorer to confirm that cost breakdown by `Environment` tag is active.

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Tags not showing up in AWS Cost Explorer**
   - *Root Cause:* User-defined cost allocation tags must be manually activated in the AWS Billing console and take up to 24 hours to begin aggregating data.
   - *Remediation:* Navigate to AWS Billing -> Cost Allocation Tags -> Select `Project`, `Environment`, `CostCenter` -> Click **Activate**.
2. **Failure Mode: Java backend fails to boot on Graviton (`m7g.large`) nodes**
   - *Root Cause:* Container image was built exclusively for `linux/amd64` instead of multi-architecture.
   - *Remediation:* Use Docker Buildx (`docker buildx build --platform linux/amd64,linux/arm64`) to build multi-arch container images that run natively on both x86 and Graviton CPUs.
3. **Failure Mode: NAT Gateway data processing bill spikes unexpectedly**
   - *Root Cause:* High volume of container image pulls or S3 uploads routing over public internet via NAT Gateway.
   - *Remediation:* Create AWS VPC Gateway Endpoints for Amazon S3 and Interface Endpoints for Amazon ECR. Traffic to S3 and ECR then stays internal to the AWS network backbone for $0.00 data transfer cost!

### G. Rollback & Disaster Recovery Procedures
To safely deprovision all cloud infrastructure and reduce monthly AWS billing to strictly $0.00:
```powershell
cd devops-configurations/terraform
terraform destroy -auto-approve
```
*Note:* Deletion protection on Aurora (`deletion_protection = false`) must be set in dev/staging to allow clean teardown.

### H. Security & Compliance Guardrails
- Cost optimization must never compromise data durability or security boundaries. Multi-AZ replication, encrypted backups, and private subnet isolation are non-negotiable architectural requirements.

### I. Production vs Local Differences

| Dimension | Local Workstation Testing | Production AWS Cloud |
| :--- | :--- | :--- |
| **Monthly Cost** | $0.00 | $150 – $350/month (Optimized Staging/Prod baseline) |
| **Compute Architecture** | Host CPU (x86-64) | AWS Graviton 3 (ARM64) instances |
| **Billing Guardrails** | None needed | Automated AWS Budgets with SMS and email kill-switches |

### J. Interview Readiness Questions & Model Answers
- **Q1: What are the three phases of the FinOps lifecycle?**
  - *Answer:* (1) **Inform:** Providing real-time cost visibility and accountability through standardized resource tagging, Cost Explorer dashboards, and showback/chargeback models. (2) **Optimize:** Continuously identifying waste through instance right-sizing, Spot/Graviton adoption, single-NAT toggles for non-prod, and storage lifecycle tiering. (3) **Operate:** Embedding cost metrics into continuous engineering operations—tracking cost per transaction as a key architectural metric and establishing automated budget alerts.
- **Q2: How do VPC Gateway Endpoints reduce both cost and latency?**
  - *Answer:* By default, when a pod inside a private subnet talks to Amazon S3 (e.g. uploading log chunks or downloading models), the traffic traverses the NAT Gateway, which charges ~$0.045 per GB for data processing. Provisioning a free VPC Gateway Endpoint for S3 injects a route directly into the VPC route tables pointing to S3. Traffic bypasses the NAT Gateway entirely, saving data processing fees and reducing network latency.
- **Q3: What is the financial benefit of AWS Graviton for Java workloads?**
  - *Answer:* AWS Graviton processors (custom ARM64 silicon built by Annapurna Labs) deliver up to 40% better price-performance compared to fifth/sixth-generation x86 instances. Because modern Java (OpenJDK 21) runs seamlessly on Linux ARM64 with highly optimized C2 JIT compilers, migrating Spring Boot workloads to Graviton (`m7g.large`) cuts compute bills by ~20% with zero application code modifications.

### K. Cost & Performance Considerations
- Estimated Monthly Staging Spend Breakdown (Optimized):
  - EKS Control Plane: ~$72.00
  - 2x `t3.medium` or `m7g.medium` Nodes: ~$60.00
  - 1x NAT Gateway (Single NAT mode): ~$32.40
  - Aurora Serverless v2 (0.5 ACU baseline): ~$43.20
  - Total Monthly Staging Footprint: ~$207.60/month.

### L. Real-World Banking Considerations
- Financial institutions calculate unit economics (e.g., "Infrastructure Cost per 10,000 Transfers"). FinOps tagging and observability enable engineering teams to prove that scaling transaction volumes does not scale infrastructure costs linearly.

### M. Progress Sign-off Checklist
- [ ] Provider default tags declared in `versions.tf`.
- [ ] Single NAT Gateway toggle verified in staging.
- [ ] AWS Budget created with 80% threshold email notification.
- [ ] VPC S3 Gateway Endpoint strategy reviewed.

### N. Next Manual Transition
Proceed to **[Manual 32: DevOps Portfolio Presentation & Staff Interview Mastery](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/32-portfolio-presentation-interview-mastery.md)** for the final capstone guide on showcasing FinCore in Senior and Staff technical interviews.

