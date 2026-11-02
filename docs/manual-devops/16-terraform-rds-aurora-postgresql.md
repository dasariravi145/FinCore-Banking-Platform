# FinCore Banking Platform — DevOps Engineering Manual
## Manual 16: Terraform Amazon RDS Aurora PostgreSQL Serverless v2

### A. Objective & Architecture Context
A core banking ledger demands zero data loss (RPO = 0), automatic multi-AZ failover (RTO < 30s), continuous backup retention, and strict ACID transaction guarantees. While local development uses a single PostgreSQL container, production FinCore runs on Amazon Aurora PostgreSQL Serverless v2 provisioned via `devops-configurations/terraform/rds.tf`. This manual provisions the Aurora cluster, custom parameter groups, security boundaries, and automated password rotation in AWS Secrets Manager.

### B. Prerequisites & Dependencies
- Manuals 14 and 15 completed (VPC, subnets, and EKS security groups active).
- AWS IAM permissions to provision RDS clusters, parameter groups, and Secrets Manager secrets.

### C. Step-by-Step Implementation Instructions

#### Step 1: Review Aurora Parameter Group & ACID Tuning
Inspect `devops-configurations/terraform/rds.tf`:
- **Family:** `aurora-postgresql16`
- **SSL Enforcement:** `rds.force_ssl = 1` (All client connections must use TLS encryption in transit).
- **Forensic Audit Logging:**
  - `log_connections = 1` and `log_disconnections = 1` (Tracks who connects and when).
  - `log_lock_waits = 1` (Logs transactions waiting longer than `deadlock_timeout` for locks).
  - `log_min_duration_statement = 250` (Logs all SQL queries taking longer than 250ms for performance tuning).

#### Step 2: Review Aurora Serverless v2 Cluster Specifications
- **Engine:** `aurora-postgresql`, version `16.2`
- **Subnet Group:** Isolated database subnets (`aws_subnet.database[*].id`).
- **Security Group:** Ingress strictly allows TCP port 5432 from the EKS Node Security Group (`aws_security_group.eks_nodes_sg.id`). No external access.
- **Credentials Management:** `manage_master_user_password = true` (Delegates password generation, storage, and rotation entirely to AWS Secrets Manager; zero plaintext passwords in Terraform state or code).
- **Scaling Limits:** `min_capacity = 0.5 ACU` (1 GB RAM), `max_capacity = 8.0 ACU` (16 GB RAM).
- **High Availability:** 2 Aurora instances (1 Primary Writer, 1 Read Replica across different AZs).

#### Step 3: Execute Terraform Plan for Aurora
Run:
```powershell
terraform plan -target=aws_rds_cluster.fincore_aurora_cluster -out=rds.tfplan
```
Verify the planned resources: DB Subnet Group, Security Group, Cluster Parameter Group, Aurora Cluster, Instances, and IAM Monitoring Role.

#### Step 4: Apply Aurora Infrastructure
Apply the configuration:
```powershell
terraform apply rds.tfplan
```
*Wait Time:* Approximately 8 to 12 minutes for Aurora cluster creation and replica synchronization.

#### Step 5: Retrieve Database Endpoint and Secret ARN
Extract connection endpoints:
```powershell
$WRITER_ENDPOINT = terraform output -raw rds_cluster_endpoint
$READER_ENDPOINT = terraform output -raw rds_cluster_reader_endpoint
$SECRET_ARN = terraform output -raw rds_master_secret_arn

Write-Host "Writer Endpoint: $WRITER_ENDPOINT"
Write-Host "Reader Endpoint: $READER_ENDPOINT"
```

### D. Configuration References
- Aurora Terraform definition: `devops-configurations/terraform/rds.tf`
- Variables: `devops-configurations/terraform/variables.tf`
- Outputs: `devops-configurations/terraform/outputs.tf`

### E. Verification & Validation Steps
Query AWS Secrets Manager to verify that master credentials were generated:
```powershell
aws secretsmanager describe-secret --secret-id $SECRET_ARN
```
Verify Aurora cluster state via AWS CLI:
```powershell
aws rds describe-db-clusters `
  --db-cluster-identifier "staging-fincore-banking-cluster" `
  --query "DBClusters[0].[Status,Endpoint,ReaderEndpoint,EngineVersion,AllocatedStorage]" `
  --output table
```
*Expected Status:* `"available"`

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `DBSubnetGroupDoesNotCoverEnoughAZs`**
   - *Root Cause:* DB subnet group contains subnets in fewer than 2 Availability Zones.
   - *Remediation:* Aurora mandates database subnets across at least 2 (preferably 3) distinct AZs. Ensure `aws_subnet.database[*].id` has 3 subnets in `vpc.tf`.
2. **Failure Mode: Spring Boot in EKS throws `Connection timed out` connecting to Aurora**
   - *Root Cause:* RDS Security Group does not allow ingress from the EKS Node Security Group, or backend pod is running in a subnet without route to database subnets.
   - *Remediation:* Verify that `aws_security_group.rds_sg` contains an ingress rule specifying `security_groups = [aws_security_group.eks_nodes_sg.id]` on port 5432.
3. **Failure Mode: `FATAL: no pg_hba.conf entry for host ..., SSL off`**
   - *Root Cause:* Aurora parameter group enforces SSL (`rds.force_ssl = 1`), but client JDBC URL did not specify SSL parameters.
   - *Remediation:* Append `?ssl=true&sslmode=verify-full` to the Spring Boot JDBC connection string in Kubernetes ConfigMaps.

### G. Rollback & Disaster Recovery Procedures
- **Point-in-Time Recovery (PITR):** Aurora continuously backs up data to Amazon S3. In the event of catastrophic data corruption, execute:
  ```powershell
  aws rds restore-db-cluster-to-point-in-time `
    --source-db-cluster-identifier "staging-fincore-banking-cluster" `
    --target-db-cluster-identifier "restored-fincore-cluster" `
    --restore-to-time "2026-11-01T12:00:00Z"
  ```
- **Automated Failover:** If the Primary Writer fails, Aurora promotes the Read Replica to Primary in under 30 seconds without modifying the cluster endpoint DNS.

### H. Security & Compliance Guardrails
- **Zero Plaintext Secrets:** `manage_master_user_password = true` prevents human access to raw database credentials.
- **Storage Encryption:** Encryption at rest enabled using AWS KMS 256-bit keys (`storage_encrypted = true`).
- **Deletion Protection:** Enabled in production (`deletion_protection = true`) to prevent accidental `terraform destroy` from wiping financial records.

### I. Production vs Local Differences

| Dimension | Local PostgreSQL Container | Amazon Aurora Serverless v2 |
| :--- | :--- | :--- |
| **Storage Architecture** | Single local Docker volume | 6-way replicated storage volume across 3 AZs |
| **Failover Model** | Container restart (downtime incurred) | Automated replica promotion in < 30 seconds |
| **Scaling** | Fixed host CPU/RAM | Dynamic scaling from 0.5 to 8.0 ACUs based on load |
| **Credential Rotation** | Static environment variable | Automated rotation via AWS Secrets Manager |

### J. Interview Readiness Questions & Model Answers
- **Q1: How does Aurora Serverless v2 handle sudden spikes in banking transactions?**
  - *Answer:* Traditional RDS instances require resizing EC2 instance types, which causes minutes of failover downtime. Aurora Serverless v2 scales CPU and memory capacity up or down dynamically in increments of 0.5 Aurora Capacity Units (ACUs, where 1 ACU = 2 GB RAM) in milliseconds without dropping client connections, ensuring transaction processing during unexpected traffic surges while saving cost during off-peak night hours.
- **Q2: Why is Aurora's storage architecture uniquely resilient for financial systems?**
  - *Answer:* Aurora decouples compute from storage. The Aurora storage volume is striped across 3 Availability Zones with 6 storage copies. Aurora can survive the loss of an entire Availability Zone plus an additional storage node without losing write availability, and can survive two storage copies being lost without losing read availability. Log processing is offloaded to the distributed storage fleet, eliminating heavy disk checkpoints.
- **Q3: How do you configure Spring Boot HikariCP connection pools for Aurora multi-AZ failover?**
  - *Answer:* We configure HikariCP with a low `maxLifetime` (e.g. 10 to 15 minutes) and a small `connectionTimeout` (e.g. 5 seconds). During an Aurora failover, the cluster endpoint DNS switches to the promoted replica. HikariCP must detect severed TCP sockets quickly via `validationTimeout` and discard stale connections rather than blocking transaction threads on a dead master node.

### K. Cost & Performance Considerations
- Aurora Serverless v2 costs ~$0.12 per ACU-hour. Running at 0.5 ACU baseline costs ~$43.20/month per instance.
- Storage costs $0.10 per GB-month plus $0.20 per million I/O requests.

### L. Real-World Banking Considerations
- Financial transactions require strict compliance with PCI-DSS Requirement 3 (Protect Stored Account Data) and Requirement 8 (Identify Users and Authenticate Access). Aurora's KMS storage encryption, parameter-level SSL enforcement, and Secrets Manager credential lifecycle fulfill these mandates out of the box.

### M. Progress Sign-off Checklist
- [ ] DB Subnet Group across 3 AZs created.
- [ ] RDS Security Group attached to EKS nodes.
- [ ] Aurora PostgreSQL cluster provisioned with 2 instances.
- [ ] Master password generated in AWS Secrets Manager.

### N. Next Manual Transition
Proceed to **[Manual 17: Terraform State Management, Drift Detection & Migration](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/17-terraform-state-management-drift-detection.md)** to establish automated drift detection and state lifecycle management.

