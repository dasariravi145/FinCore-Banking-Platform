# FinCore Banking Platform — DevOps Engineering Manual
## Manual 13: AWS Account Setup, IAM Least Privilege & S3 Remote State Backend

### A. Objective & Architecture Context
Before provisioning cloud infrastructure via Infrastructure as Code (IaC), you must establish a secure foundation in Amazon Web Services. Storing Terraform state files locally on a developer workstation risks state file corruption, unencrypted secrets leakage, and race conditions during concurrent modifications. This manual establishes an encrypted AWS S3 bucket with versioning and an AWS DynamoDB table for distributed state locking, configured according to banking security standards.

### B. Prerequisites & Dependencies
- An active AWS Account with Administrator credentials.
- AWS CLI v2 installed on your local workstation (`aws --version`).
- Preferred AWS Region selected (Default: `us-east-1` or `eu-west-1`).
- Monthly budget alert configured in AWS Billing to prevent unexpected charges.

### C. Step-by-Step Implementation Instructions

#### Step 1: Configure AWS CLI Credentials
Execute in PowerShell:
```powershell
aws configure
```
Provide your AWS Access Key ID, Secret Access Key, default region (e.g. `us-east-1`), and output format (`json`).
Verify identity:
```powershell
aws sts get-caller-identity
```

#### Step 2: Create the Encrypted S3 Bucket for Terraform State
Generate a globally unique bucket name (e.g., `fincore-tf-state-<account-id>`):
```powershell
$ACCOUNT_ID = (aws sts get-caller-identity --query "Account" --output text)
$BUCKET_NAME = "fincore-tf-state-$ACCOUNT_ID"
$REGION = "us-east-1"

# Create Bucket
aws s3api create-bucket `
  --bucket $BUCKET_NAME `
  --region $REGION

# Enable Versioning (MANDATORY for state rollback)
aws s3api put-bucket-versioning `
  --bucket $BUCKET_NAME `
  --versioning-configuration Status=Enabled

# Enforce Server-Side Encryption (SSE-S3 / AES256)
aws s3api put-bucket-encryption `
  --bucket $BUCKET_NAME `
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {
        "SSEAlgorithm": "AES256"
      }
    }]
  }'

# Block All Public Access (MANDATORY Security Guardrail)
aws s3api put-public-access-block `
  --bucket $BUCKET_NAME `
  --public-access-block-configuration '{
    "BlockPublicAcls": true,
    "IgnorePublicAcls": true,
    "BlockPublicPolicy": true,
    "RestrictPublicBuckets": true
  }'
```

#### Step 3: Create DynamoDB Table for Distributed State Locking
Create the DynamoDB lock table with the mandatory partition key `LockID`:
```powershell
aws dynamodb create-table `
  --table-name fincore-tf-locks `
  --attribute-definitions AttributeName=LockID,AttributeType=S `
  --key-schema AttributeName=LockID,KeyType=HASH `
  --billing-mode PAY_PER_REQUEST `
  --region $REGION
```

#### Step 4: Update Terraform Backend Configuration
Inspect `devops-configurations/terraform/backend.tf`:
```hcl
terraform {
  backend "s3" {
    bucket         = "REPLACE_WITH_YOUR_STATE_BUCKET_NAME"
    key            = "fincore/infrastructure/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "fincore-tf-locks"
    encrypt        = true
  }
}
```
Update the `bucket` attribute with your actual `$BUCKET_NAME`.

### D. Configuration References
- Backend template: `devops-configurations/terraform/backend.tf`
- Provider versions: `devops-configurations/terraform/versions.tf`
- Input variables: `devops-configurations/terraform/variables.tf`

### E. Verification & Validation Steps
Verify that the S3 bucket and DynamoDB table exist and are configured correctly:
```powershell
aws s3api get-bucket-versioning --bucket $BUCKET_NAME
aws dynamodb describe-table --table-name fincore-tf-locks --query "Table.TableStatus"
```
Expected output:
- Bucket versioning: `"Status": "Enabled"`
- DynamoDB TableStatus: `"ACTIVE"`

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `BucketAlreadyExists` or `BucketAlreadyOwnedByOthers`**
   - *Root Cause:* S3 bucket names are globally unique across all AWS accounts worldwide.
   - *Remediation:* Append your unique 12-digit AWS Account ID or a random UUID to the bucket name.
2. **Failure Mode: `AccessDenied` when running S3 or DynamoDB commands**
   - *Root Cause:* IAM user lacks permissions for `s3:CreateBucket` or `dynamodb:CreateTable`.
   - *Remediation:* Ensure your IAM policy includes administrative permissions or attach the AWS managed policy `PowerUserAccess`.
3. **Failure Mode: `Error acquiring the state lock` during Terraform init/apply**
   - *Root Cause:* A previous Terraform process crashed or was killed while holding the lock.
   - *Remediation:* Inspect the lock table via `aws dynamodb get-item --table-name fincore-tf-locks --key '{"LockID":{"S":"fincore/infrastructure/terraform.tfstate-md5"}}'`. If verified that no other process is running, release with `terraform force-unlock <LOCK-ID>`.

### G. Rollback & Disaster Recovery Procedures
If a Terraform state file becomes corrupted:
1. List previous state versions in the S3 bucket:
   ```powershell
   aws s3api list-object-versions --bucket $BUCKET_NAME --prefix "fincore/infrastructure/terraform.tfstate"
   ```
2. Restore the previous known good version by copying the specific `VersionId` back to the current object.

### H. Security & Compliance Guardrails
- **Zero Public Access:** S3 Public Access Block prevents catastrophic internet exposure of Terraform state files, which may contain sensitive database usernames or certificates.
- **Encryption at Rest:** All state files are encrypted via AES256 or AWS KMS before being written to disk.
- **Least Privilege:** CI/CD runners should assume an IAM role with permission restricted strictly to the FinCore VPC and EKS resource ARNs.

### I. Production vs Local Differences

| Dimension | Local Developer Testing | Production Cloud Setup |
| :--- | :--- | :--- |
| **State Storage** | `terraform.tfstate` on local disk | Versioned S3 bucket with customer-managed KMS key |
| **Concurrency** | Single-user; no locking required | Distributed DynamoDB locking preventing race conditions |
| **Credential Type** | Local AWS CLI profile / Access Keys | Ephemeral IAM Roles via GitHub Actions / Jenkins OIDC |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why is remote state locking with DynamoDB mandatory in enterprise Terraform workflows?**
  - *Answer:* When multiple engineers or automated CI/CD pipelines run Terraform simultaneously, concurrent `apply` operations without locking will read and write to the state file concurrently. This leads to state file corruption, resource duplication, or accidental deletion of infrastructure. DynamoDB uses atomic conditional writes on the `LockID` key to ensure only one process can mutate state at any given moment.
- **Q2: Why must S3 bucket versioning be enabled for Terraform state storage?**
  - *Answer:* S3 versioning ensures that every mutation to `terraform.tfstate` creates an immutable historical version rather than overwriting in place. If an engineer accidentally executes a destructive command or state corruption occurs, we can immediately restore the exact previous state file version, preventing disaster recovery downtime.
- **Q3: How do you prevent sensitive variables (like DB passwords) from leaking in Terraform state?**
  - *Answer:* First, mark input variables as `sensitive = true` so Terraform masks them in console logs. Second, utilize AWS Secrets Manager or HashiCorp Vault to generate dynamic credentials at runtime rather than declaring static passwords in HCL. Third, treat the S3 remote state bucket as a tier-1 security asset with strict IAM policies and KMS encryption.

### K. Cost & Performance Considerations
- DynamoDB `PAY_PER_REQUEST` billing mode costs approximately $0.00/month for state locking (less than a few hundred lock requests per month).
- S3 storage costs for small state files (< 5 MB) are fractions of a cent per month.

### L. Real-World Banking Considerations
- PCI-DSS and financial regulators require audit trails for all infrastructure mutations. S3 server access logging and AWS CloudTrail must be enabled to track every read, write, and lock release on the Terraform state backend.

### M. Progress Sign-off Checklist
- [ ] AWS CLI configured and identity verified.
- [ ] Encrypted S3 bucket created with versioning enabled and public access blocked.
- [ ] DynamoDB `fincore-tf-locks` table created with `LockID` key.
- [ ] `backend.tf` updated with unique bucket name.

### N. Next Manual Transition
Proceed to **[Manual 14: Terraform Multi-AZ VPC Networking](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/14-terraform-vpc-networking-multi-az.md)** to provision the enterprise network topology.

