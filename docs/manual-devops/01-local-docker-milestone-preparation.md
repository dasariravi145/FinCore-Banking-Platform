# FinCore Banking Platform — DevOps Engineering Manual
## Manual 01: Local Docker Milestone Preparation & Host Guardrails

### A. Objective & Architecture Context
FinCore Banking Platform requires a containerized local development stack simulating enterprise production topologies. The primary objective of this manual is to prepare the host Windows workstation, configure Docker Desktop with WSL2 integration, audit network port availability, and guarantee zero disruption to existing host database services before provisioning containers.

### B. Prerequisites & Dependencies
- **OS:** Windows 10/11 with WSL2 (Ubuntu 22.04 LTS distribution).
- **Docker Desktop:** Version 4.30+ installed.
- **PowerShell:** Version 7.x or Windows PowerShell 5.1 running with Administrator privileges.
- **Resource Allocation in Docker Desktop Settings:**
  - Memory: Minimum 6 GB assigned to WSL2 (`.wslconfig`).
  - CPU: Minimum 4 vCPUs.
  - Swap: 2 GB.

### C. Step-by-Step Implementation Instructions

#### Step 1: Verify Windows Subsystem for Linux (WSL2)
Open PowerShell as Administrator and run:
```powershell
wsl --status
```
Verify that the default version is set to 2. If not, set it:
```powershell
wsl --set-default-version 2
```

#### Step 2: Configure Global WSL Resource Limits (`.wslconfig`)
To prevent Docker and WSL2 from consuming all host RAM and crashing IDEs, create or update `C:\Users\<YOUR_USER>\.wslconfig`:
```ini
[wsl2]
memory=8GB
processors=4
swap=2GB
localhostForwarding=true
```
Restart WSL to apply settings:
```powershell
wsl --shutdown
```

#### Step 3: Verify Host Network Ports and Identify Collisions
The host workstation currently runs a native Windows PostgreSQL 18 service on port 5432 and another project on port 5434. Run the port audit:
```powershell
Get-NetTCPConnection -LocalPort 5432, 5434, 5435, 8080, 80, 5173 -ErrorAction SilentlyContinue |
    Format-Table LocalAddress, LocalPort, State, OwningProcess -AutoSize
```
- Port `5432`: Listening by Windows `postgres.exe` (DO NOT STOP).
- Port `5434`: Listening by external project (DO NOT TOUCH).
- Port `5435`: Must be completely UNBOUND. This is FinCore's dedicated database port.

#### Step 4: Verify Docker Engine Readiness
Launch Docker Desktop from the Windows Start menu. Wait until the whale icon in the system tray turns steady green. Execute:
```powershell
docker info --format '{{json .ServerVersion}}'
docker compose version
```

### D. Configuration References
- Template file: `devops-configurations/docker/.dockerignore`
- Template file: `devops-configurations/docker/.env.docker.example`
- Environment mapping: `.env.docker.example` defines `POSTGRES_HOST_PORT=5435`.

### E. Verification & Validation Steps
Run the following PowerShell one-liner to confirm Docker daemon connectivity:
```powershell
docker run --rm hello-world
```
Expected output:
```
Hello from Docker!
This message shows that your installation appears to be working correctly.
```

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: `docker : The term 'docker' is not recognized`**
   - *Root Cause:* Docker Desktop CLI directory is not in Windows system `PATH`.
   - *Remediation:* Add `C:\Program Files\Docker\Docker\resources\bin` to user or system Environment Variable `Path`. Restart terminal.
2. **Failure Mode: `error during connect: This error may indicate that the docker daemon is not running.`**
   - *Root Cause:* Docker Desktop background service is stopped or WSL2 backend is initializing.
   - *Remediation:* Open Services (`services.msc`), ensure "Docker Desktop Service" is set to Automatic and Running. Execute `wsl --shutdown` and restart Docker Desktop.
3. **Failure Mode: Port 5435 is unexpectedly occupied.**
   - *Root Cause:* A stray process or previous container session is bound to 5435.
   - *Remediation:* Identify the PID via `Get-NetTCPConnection -LocalPort 5435 | Select-Object OwningProcess`. Inspect process via `Get-Process -Id <PID>`. Terminate safely.

### G. Rollback & Disaster Recovery Procedures
If Docker Desktop instability causes workstation slowdowns:
```powershell
wsl --shutdown
Stop-Process -Name "Docker Desktop" -Force -ErrorAction SilentlyContinue
```
No host files or PostgreSQL databases are altered during this preparation stage.

### H. Security & Compliance Guardrails
- Ensure Docker Desktop is configured to use rootless containers in WSL2 where supported.
- Never grant unauthenticated network access to Docker daemon TCP socket (`tcp://0.0.0.0:2375`).
- Ensure Windows Defender firewall allows inbound connections on ports 8080 and 80 only for private/internal interfaces.

### I. Production vs Local Differences

| Dimension | Local Workstation (Milestone 1) | Production (AWS / EKS) |
| :--- | :--- | :--- |
| **Engine** | Docker Desktop on Windows WSL2 | Containerd on Amazon Linux 2023 |
| **Port Mapping** | Host port 5435 published to container | ClusterIP internal routing; no published host ports |
| **Daemon Access** | Named pipe (`\\.\pipe\docker_engine`) | Kubernetes API server via mTLS certificates |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why did you isolate PostgreSQL to host port 5435 instead of reconfiguring the host service on 5432?**
  - *Answer:* In enterprise workstations and multi-tenant developer environments, altering or terminating existing host services risks breaking legacy applications or uncommitted data. By decoupling the container publishing port (5435:5432), we achieved 100% container encapsulation without modifying host operating system state.
- **Q2: What is the impact of WSL2 memory consumption on Java build processes?**
  - *Answer:* WSL2 uses dynamic memory allocation up to 50% or 80% of total host RAM. Without a `.wslconfig` limit, heavy builds can cause Windows memory starvation. Capping WSL2 memory at 8GB guarantees stability for both the host IDE and the virtualized container runtime.
- **Q3: How does Docker on WSL2 handle file I/O performance differences between Windows drives and WSL filesystems?**
  - *Answer:* Accessing mounted Windows paths (`/mnt/d/...`) from Linux containers incurs significant 9P protocol translation overhead. In production CI pipelines or heavy build steps, working within the native Linux ext4 filesystem yields up to 5x faster I/O operations.

### K. Cost & Performance Considerations
- Local Docker execution costs $0.00 cloud spend.
- CPU allocation should be tuned to prevent thermal throttling on developer laptops during Maven multi-thread packaging (`mvn -T 1C clean package`).

### L. Real-World Banking Considerations
- Banking developers are frequently issued locked-down Windows enterprise laptops. Understanding how to navigate restricted corporate group policies, Zscaler proxy intercepts, and custom internal CA certificates during Docker base image pulls is essential.

### M. Progress Sign-off Checklist
- [ ] Docker Desktop version 4.30+ confirmed running.
- [ ] `.wslconfig` configured with memory and swap limits.
- [ ] Port 5435 verified completely free.
- [ ] `docker run --rm hello-world` successfully executed.

### N. Next Manual Transition
Proceed to **[Manual 02: PostgreSQL Container Isolation & Flyway Migrations](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/02-postgresql-container-isolation-flyway.md)** to provision the isolated database container and verify schema migration consistency.

