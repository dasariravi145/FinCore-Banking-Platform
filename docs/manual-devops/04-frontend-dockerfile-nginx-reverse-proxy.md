# FinCore Banking Platform — DevOps Engineering Manual
## Manual 04: Frontend Dockerfile, Nginx Reverse Proxy & Static Asset Optimization

### A. Objective & Architecture Context
FinCore's user interface is a responsive React 18 / TypeScript single-page application (SPA) bundled via Vite. In production, serving SPAs via Node development servers introduces severe memory bloat and single-threaded performance bottlenecks. This manual instructs how to compile static production assets with Node 22 Alpine and serve them through an optimized Nginx 1.27 Alpine container that acts as both a high-throughput static file server and an internal reverse proxy for backend API calls.

### B. Prerequisites & Dependencies
- Docker Desktop active.
- Node.js 20+ and npm installed on host for local testing (optional, build happens in container).
- Frontend codebase located under `frontend/`.

### C. Step-by-Step Implementation Instructions

#### Step 1: Inspect Multi-Stage Build & Nginx Architecture
1. **Build Stage (`node:22-alpine`):** Copies `package.json` and `package-lock.json`, runs `npm ci` for deterministic dependency resolution, copies source code, and runs `npm run build` to generate compiled static assets in `/app/dist`.
2. **Runtime Stage (`nginx:1.27-alpine`):** Strips default Nginx sample configs, copies compiled distribution to `/usr/share/nginx/html`, and applies custom `nginx.conf`.

#### Step 2: Build the Frontend Image Locally
Execute from the project root:
```powershell
docker build `
  -f devops-configurations/docker/frontend.Dockerfile `
  -t fincore-frontend:1.0.0 `
  -t fincore-frontend:latest .
```

#### Step 3: Inspect Nginx Reverse Proxy Routing Configuration
Inspect `devops-configurations/nginx/nginx.conf`:
- **SPA Fallback:** `try_files $uri $uri/ /index.html;` ensures client-side routes (e.g. `/transfers`, `/accounts/:id`, `/admin`) reload without 404 errors.
- **API Proxying:** Requests to `/api/` are forwarded directly to `http://fincore-backend:8080/api/` with essential headers (`Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`).
- **Health Proxying:** Requests to `/actuator/health` are forwarded to the backend for unified probing.
- **Static Caching:** Gzip compression enabled; static assets (`.js`, `.css`, images) cached with `Cache-Control: public, max-age=31536000, immutable`.
- **Security Headers:** `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`.

### D. Configuration References
- Dockerfile: `devops-configurations/docker/frontend.Dockerfile`
- Nginx configuration: `devops-configurations/nginx/nginx.conf`
- Static distribution source: `frontend/dist/`

### E. Verification & Validation Steps
Test running the frontend container standalone:
```powershell
docker run -d `
  --name fincore-frontend-test `
  --network fincore-network `
  -p 80:80 `
  -p 5173:80 `
  fincore-frontend:latest
```
Verify HTTP response:
```powershell
curl -I http://localhost
```
Expected headers:
```http
HTTP/1.1 200 OK
Server: nginx/...
Content-Type: text/html
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
```
Clean up test container:
```powershell
docker stop fincore-frontend-test && docker rm fincore-frontend-test
```

### F. Troubleshooting & Common Failure Modes
1. **Failure Mode: Browser shows 404 when refreshing deep URLs like `http://localhost/transfers`**
   - *Root Cause:* Nginx attempted to locate a physical file `/usr/share/nginx/html/transfers` which does not exist.
   - *Remediation:* Ensure `try_files $uri $uri/ /index.html;` is present inside the `location /` block of `nginx.conf`.
2. **Failure Mode: Frontend API requests return `502 Bad Gateway`**
   - *Root Cause:* Nginx cannot resolve or reach `http://fincore-backend:8080`.
   - *Remediation:* Verify that the backend container is running on the same Docker bridge network (`fincore-network`) and named `fincore-backend`.
3. **Failure Mode: Static assets fail with `403 Forbidden`**
   - *Root Cause:* File permission mismatch when copying files from builder stage.
   - *Remediation:* Ensure files in `/usr/share/nginx/html` are world-readable (`chmod -R 755 /usr/share/nginx/html`).

### G. Rollback & Disaster Recovery Procedures
To roll back the frontend to a known good image:
```powershell
docker stop fincore-frontend
docker run -d --name fincore-frontend --network fincore-network -p 80:80 fincore-frontend:1.0.0
```

### H. Security & Compliance Guardrails
- Nginx version token hidden (`server_tokens off;`).
- Frame embedding blocked (`X-Frame-Options: DENY`) to prevent Clickjacking attacks against banking balance transfer forms.
- MIME type sniffing blocked (`X-Content-Type-Options: nosniff`).

### I. Production vs Local Differences

| Dimension | Local Docker (`fincore-frontend`) | Production (AWS CloudFront + S3 / Ingress) |
| :--- | :--- | :--- |
| **Hosting Model** | Nginx container reverse-proxying API | S3 static hosting fronted by CloudFront CDN edge caches |
| **API Routing** | Nginx `proxy_pass` to backend container | AWS Application Load Balancer / API Gateway path routing |
| **SSL/TLS** | Plain HTTP on host port 80 / 5173 | TLS 1.3 terminated at CloudFront / AWS ALB with ACM certs |

### J. Interview Readiness Questions & Model Answers
- **Q1: Why use Nginx to serve single-page React apps instead of running `npm start` / Node server in production?**
  - *Answer:* Node development servers are designed for developer hot-reloading, not production scale. They consume 150–300 MB of RAM, are single-threaded, and present security risks. Nginx is an event-driven, non-blocking C-based server that consumes < 15 MB of RAM and can saturate 10Gbps network links serving pre-compressed static assets.
- **Q2: How does `try_files $uri $uri/ /index.html;` solve SPA client-side routing?**
  - *Answer:* In SPAs, routing between screens (e.g. `/loans`, `/admin`) is handled entirely by JavaScript in the browser. When a user bookmarks or refreshes a URL, the browser requests `/loans` from the web server. Without `try_files`, the web server returns a 404 because no `/loans` directory exists. `try_files` instructs Nginx to fall back to `/index.html`, allowing the React router to mount and render the proper component.
- **Q3: What security headers are mandatory on banking frontend web servers?**
  - *Answer:* `Content-Security-Policy` (prevents XSS), `X-Frame-Options: DENY` (prevents Clickjacking), `X-Content-Type-Options: nosniff` (prevents MIME sniffing), `Strict-Transport-Security` (enforces HTTPS), and `Referrer-Policy: strict-origin-when-cross-origin`.

### K. Cost & Performance Considerations
- Gzip compression (`gzip_comp_level 6`) reduces CSS/JS payload sizes by ~70%, dramatically improving mobile load times and reducing network egress bandwidth costs.

### L. Real-World Banking Considerations
- Banking SPAs must never cache sensitive user data in the browser's persistent storage. Dynamic API responses carry `Cache-Control: no-store, no-cache, must-revalidate` to prevent cached financial account records from surviving browser sessions on shared terminals.

### M. Progress Sign-off Checklist
- [ ] `frontend.Dockerfile` builds cleanly.
- [ ] Nginx container starts and serves FinCore bundle on port 80.
- [ ] Reverse proxy forwarding to `/api/` verified.
- [ ] Security headers confirmed in curl response.

### N. Next Manual Transition
Proceed to **[Manual 05: Docker Compose Full-Stack Orchestration](file:///D:/Projects/FinCore-Banking-Platform/docs/manual-devops/05-docker-compose-fullstack-orchestration.md)** to orchestrate database, backend, and frontend into a single multi-service topology.

