# FinCore Banking Platform — Prerequisites & Tooling Guide

Before executing the manuals, verify that your local workstation meets the minimum system requirements and has the necessary developer and DevOps tools installed.

---

### 1. Workstation Hardware & OS Specifications

- **Operating System:** Windows 10/11 Pro, Enterprise, or Education (WSL2 enabled).
- **CPU:** Minimum 4 physical cores (8 threads recommended).
- **RAM:** Minimum 16 GB (24–32 GB strongly recommended for running multi-container Docker Compose or Minikube alongside IDEs).
- **Storage:** At least 40 GB free NVMe/SSD space for Docker images, Maven cache, and local logs.

---

### 2. Software & Tooling Matrix

| Tool | Minimum Version | Installation Check Command | Purpose |
| :--- | :--- | :--- | :--- |
| **Java Development Kit (JDK)** | OpenJDK 21.x | `java -version` | Backend execution and compilation |
| **Apache Maven** | 3.9.x | `mvn -version` | Java dependency management & packaging |
| **Node.js & npm** | Node 20+ / npm 10+ | `node -v` ; `npm -v` | Frontend build and testing |
| **Docker Desktop** | 4.30+ | `docker version` | Container engine & Compose |
| **WSL2** | Ubuntu 22.04 LTS | `wsl --status` | Linux kernel virtualization for Docker |
| **Git** | 2.40+ | `git --version` | Version control & GitOps repository |
| **AWS CLI** | 2.15+ | `aws --version` | Cloud infrastructure management |
| **Terraform** | 1.8.x+ | `terraform -version` | Infrastructure as Code provisioning |
| **kubectl** | 1.30.x | `kubectl version --client` | Kubernetes cluster interaction |
| **Helm** | 3.14.x+ | `helm version` | Kubernetes application packaging |
| **PostgreSQL Client (psql)** | 16.x or 18.x | `psql --version` | Database query execution & verification |
| **curl** | 8.x+ | `curl --version` | API endpoint testing & health probing |

---

### 3. Local Port Architecture & Conflict Matrix

The following table documents host port allocation on your Windows machine:

```
+--------------------------------------------------------------------------+
| HOST PORT ALLOCATION MAP                                                 |
+--------------------------------------------------------------------------+
| 5432 : Local Windows PostgreSQL 18 Service (DO NOT STOP / DO NOT MODIFY) |
| 5434 : External Project Local Database    (DO NOT TOUCH)                |
| 5435 : FinCore Isolated PostgreSQL Container [fincore-db] (TARGET)       |
| 8080 : FinCore Spring Boot Backend [fincore-backend]                     |
| 80   : FinCore Nginx Production Reverse Proxy [fincore-frontend]         |
| 5173 : FinCore Vite Development Server / Host Mapping                    |
| 9090 : Prometheus Monitoring Server (Part 5)                             |
| 3000 : Grafana Visualization Dashboards (Part 5)                         |
| 3100 : Grafana Loki Log Aggregator (Part 5)                              |
+--------------------------------------------------------------------------+
```

#### Pre-Flight Port Verification Command:
Run this PowerShell command in an elevated prompt before starting containers:

```powershell
Get-NetTCPConnection -LocalPort 5435, 8080, 80, 5173 -ErrorAction SilentlyContinue | 
    Select-Object LocalAddress, LocalPort, State, OwningProcess
```
*Expected Result:* Port 5435 must return no listener.

---

### 4. Cloud Accounts & Access Requirements (Parts 3–6)

For the cloud implementation phases (Manuals 13–32):
- **AWS Account:** Active AWS account with Administrator or PowerUser IAM credentials.
- **AWS Budget Alert:** Set a hard budget alert at \$15.00–\$25.00 USD to avoid surprise charges.
- **AWS Region:** Default to `us-east-1` or `eu-west-1` for full service and Graviton availability.
- **Docker Hub / GitHub Packages Account:** For pushing container images if AWS ECR is not used.

