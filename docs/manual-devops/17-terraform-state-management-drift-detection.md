# FinCore Banking Platform — DevOps Engineering Manual
## Manual 17: Terraform State Management, Drift Detection & State Migration

### A. Objective & Architecture Context
Infrastructure as Code (IaC) is only as reliable as the accuracy of its state representation. In production environments, unauthorized manual modifications in the AWS Console (e.g. an engineer manually editing a security group rule or resizing an EC2 instance) cause "configuration drift". If left unchecked, subsequent automated Terraform runs can unintentionally revert emergency fixes or crash production clusters. This manual details state management commands, state recovery, and automated drift detection using `terraform plan -detailed-exitcode`.

### B. Prerequisites & Dependencies
- Manuals 13–16 completed (VPC, EKS, RDS provisioned in AWS).
- Terraform CLI 1.8+ configured with remote S3 and DynamoDB backend.
- AWS CLI configured with read-only audit permissions.

### C. Step-by-Step Implementation Instructions

#### Step 1: Inspect Current Infrastructure State
Navigate to `devops-configurations/terraform/` and list all managed resources:
```powershell
cd D:\Projects\FinCore-Banking-Platform\devops-configurations\terraform
terraform state list
```
Inspect a specific resource's live state attributes:
```powershell
terraform state show aws_rds_cluster.fincore_aurora_cluster
```

#### Step 2: Test Distributed State Locking
Simulate a lock release or force-unlock if a previous process crashed:
```powershell
# In the rare event a stale lock remains in DynamoDB:
# terraform force-unlock <LOCK-ID>
```
*Rule:* Only execute `force-unlock` after verifying that no other engineer or CI pipeline is currently running.

#### Step 3: Execute Automated Drift Detection
Run a speculative plan with the `-detailed-exitcode` flag:
```powershell
terraform plan -detailed-exitcode -no-color
$EXIT_CODE = $LASTEXITCODE
Write-Host "Terraform Exit Code: $EXIT_CODE"
```
Interpreting Exit Codes:
- `0`: Success, zero infrastructure changes needed (No drift).
- `1`: Terraform encountered an evaluation or connectivity error.
- `2`: Succeeded with non-empty diff (Infrastructure Drift Detected!).

#### Step 4: Automate Drift Detection via PowerShell / CI Script
Create a scheduled drift monitor script:
```powershell
# devops-configurations/scripts/check-terraform-drift.ps1
param (
    [string]$WorkingDir = "devops-configurations/terraform"
)

Set-Location $WorkingDir
terraform init -reconfigure
terraform plan -detailed-exitcode -no-color | Out-File -FilePath "drift-report.txt"
$code = $LASTEXITCODE

if ($code -eq 0) {
    Write-Host "[PASSED] Cloud infrastructure is 100% in sync with Terraform code." -ForegroundColor Green
    exit 0
} elseif ($code -eq 2) {
    Write-Host "[ALERT] Infrastructure drift detected! Differences logged to drift-report.txt" -ForegroundColor Red
    # In CI/CD, trigger Slack/PagerDuty notification
    exit 2
} else {
    Write-Host "[ERROR] Terraform execution error occurred." -ForegroundColor Yellow
    exit 1
}
```

### D. Configuration References
- Backend definition: `devops-configurations/terraform/backend.tf`
- Drift monitor script: `devops-configurations/scripts/check-terraform-drift.ps1`
- Terraform outputs: `devops-configurations/terraform/outputs.tf`

### E. Verification & Validation Steps
Simulate intentional drift to verify the detection system:
1. Open AWS Management Console -> EC2 -> Security Groups.
2. Add a temporary tag to the VPC: `DriftTest = True`.
3. Run the drift detection script:
   ```powershell
   powershell -ExecutionPolicy Bypass -File devops-configurations/scripts/check-terraform-drift.ps1
   ```
4. Verify that Terraform detects the rogue tag and exits with code `2`.
5. Run `terraform apply` to automatically reconcile and strip the unauthorized manual tag.

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `Error: Resource already managed by Terraform`**
   - *Root Cause:* An engineer re-declared a resource block that already exists in state.
   - *Remediation:* Check `terraform state list`. If moving between modules, use `terraform state mv <source> <destination>` rather than deleting and recreating.
2. **Failure Mode: `ResourceNotFoundException` during `terraform refresh`**
   - *Root Cause:* A resource was manually deleted directly from the AWS Console.
   - *Remediation:* Run `terraform plan`. Terraform will recognize the missing resource and generate a plan to recreate it from scratch.
3. **Failure Mode: State file contains unencrypted sensitive database password**
   - *Root Cause:* Legacy modules defined static password strings in outputs.
   - *Remediation:* Use `manage_master_user_password = true` in RDS and mark output variables with `sensitive = true`.

### G. Rollback & Disaster Recovery Procedures
If a corrupted state file prevents Terraform from running:
1. Identify the previous valid version ID from S3:
   ```powershell
   aws s3api list-object-versions --bucket <bucket-name> --prefix "fincore/infrastructure/terraform.tfstate"
   ```
2. Download the good version:
   ```powershell
   aws s3api get-object --bucket <bucket-name> --key "fincore/infrastructure/terraform.tfstate" --version-id <VERSION_ID> recovered.tfstate
   ```
3. Push the recovered state to the backend:
   ```powershell
   terraform state push recovered.tfstate
   ```

### H. Security & Compliance Guardrails
- Direct write access to the S3 Terraform state bucket is restricted to the CI/CD deployment role. Individual developer IAM accounts have read-only or zero access.
- S3 Object Lock can be enabled with compliance mode for regulatory legal holds.

### I. Production vs Local Differences

| Dimension | Local Developer Workstation | Production Cloud Pipeline |
| :--- | :--- | :--- |
| **State Inspection** | Interactive terminal commands | Automated drift detection cron running every 4 hours |
| **Modification Rights** | Speculative plans (`terraform plan`) | Only approved Git merge to `main` can execute `apply` |
| **Alerting** | Console output | PagerDuty / Slack alerts dispatched upon drift detection |

### J. Interview Readiness Questions & Model Answers
- **Q1: How do you handle configuration drift in an enterprise environment?**
  - *Answer:* We handle drift through automated detection and policy enforcement. We run a scheduled CI pipeline executing `terraform plan -detailed-exitcode` every 4 hours. If exit code `2` is returned, an alert fires. For authorized emergency console changes, the engineer must backport the change into HCL code via pull request. For unauthorized changes, the automated pipeline executes `terraform apply` to overwrite and reconcile the drift back to the declared Git state.
- **Q2: What is the risk of running `terraform refresh` blindly in modern Terraform?**
  - *Answer:* In older Terraform versions, `terraform refresh` modified state immediately in place without showing a diff, potentially recording broken intermediate states. In modern Terraform, refresh is integrated into `terraform plan`. Running speculative plans allows engineers to preview exactly how live infrastructure differs from state before writing any updates.
- **Q3: How do you safely refactor Terraform resources into reusable modules without destroying live databases?**
  - *Answer:* We use `moved` blocks (introduced in Terraform 1.1+) or `terraform state mv`. A `moved` block declaratively maps `from = aws_instance.web` to `to = module.web.aws_instance.this`. When Terraform evaluates the configuration, it updates the state file pointers without generating destroy-and-recreate actions, ensuring zero downtime for live infrastructure.

### K. Cost & Performance Considerations
- Running automated drift detection consumes $0.00 infrastructure cost beyond negligible AWS API request fees.

### L. Real-World Banking Considerations
- Financial auditors require documentation demonstrating that no unauthorized manual changes bypass the change control process. Daily drift detection reports provide proof of compliance with change management standards (SOC 2 CC8.1).

### M. Progress Sign-off Checklist
- [ ] `terraform state list` successfully executed.
- [ ] Distributed locking verified in DynamoDB.
- [ ] Automated drift detection script tested.
- [ ] Speculative plan returns exit code 0 (In Sync).

### N. Next Manual Transition
Proceed to **[Manual 18: Kubernetes Core Manifests & Pod Architecture](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/18-kubernetes-core-manifests-architecture.md)** to begin Part 4 (Kubernetes, Helm & GitOps Delivery).

