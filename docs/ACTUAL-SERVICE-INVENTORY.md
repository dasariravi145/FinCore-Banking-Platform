# FinCore Banking Platform — Actual Service Inventory & API Catalog

**Document Version:** 1.0.0 (Phase 05/06 Final Audit)  
**Classification:** Engineering Internal / DevOps Reference  
**Last Updated:** November 2026  

---

## 1. Service Runtime Inventory

| Service Identifier | Type / Artifact | Runtime & Version | Default Port | Internal URLs / Endpoints |
|---|---|---|---|---|
| `fincore-banking-backend` | Spring Boot Standalone JAR | Java 21 OpenJDK / Spring Boot 3.3.6 | `8080` | `http://localhost:8080`<br/>Swagger: `/swagger-ui/index.html`<br/>Health: `/actuator/health` |
| `fincore-banking-frontend` | Single Page Application (SPA) | Node.js 22.17.0 / React 19.3.1 / Vite 8.3.5 | `5173` | `http://localhost:5173`<br/>Proxies `/api` and `/actuator` to `http://localhost:8080` |
| `fincore_db` | Relational Database | PostgreSQL 18 (Local) / H2 In-Memory (Test) | `5432` | `jdbc:postgresql://localhost:5432/fincore_db` |

---

## 2. Environment Variables Inventory

*(Variable names only — no credentials or secrets)*

| Environment Variable | Service | Default / Sample Value | Required / Optional | Description |
|---|---|---|---|---|
| `SERVER_PORT` | Backend | `8080` | Optional | HTTP listener port for Spring Boot. |
| `SPRING_PROFILES_ACTIVE` | Backend | `local` | Required | Active profile: `local` (PostgreSQL), `test` (H2). |
| `DB_HOST` | Backend | `localhost` | Required | Hostname of the PostgreSQL database server. |
| `DB_PORT` | Backend | `5432` | Required | TCP port of the PostgreSQL database server. |
| `DB_NAME` | Backend | `fincore_db` | Required | Dedicated database name. |
| `DB_USERNAME` | Backend | `fincore_user` | Required | Database user with DDL and DML permissions. |
| `DB_PASSWORD` | Backend | *(Secret)* | Required | Database user password. |
| `DB_MAX_POOL_SIZE` | Backend | `10` | Optional | HikariCP maximum connection pool size. |
| `DB_MIN_IDLE` | Backend | `5` | Optional | HikariCP minimum idle connections. |
| `FLYWAY_ENABLED` | Backend | `true` | Optional | Enable automatic Flyway schema migrations on boot. |
| `FLYWAY_BASELINE_ON_MIGRATE` | Backend | `true` | Optional | Allow baseline migration on populated schemas. |
| `JWT_SECRET` | Backend | *(Secret 256-bit+)* | Required | Cryptographic secret for signing HMAC-SHA256 tokens. |
| `JWT_ISSUER` | Backend | `fincore-banking-platform` | Optional | Issuer claim in JWT access tokens. |
| `JWT_AUDIENCE` | Backend | `fincore-banking-clients` | Optional | Audience claim in JWT access tokens. |
| `JWT_ACCESS_EXPIRATION_MS` | Backend | `900000` (15 min) | Optional | Lifetime of access tokens in milliseconds. |
| `JWT_REFRESH_EXPIRATION_MS` | Backend | `604800000` (7 days) | Optional | Lifetime of refresh tokens in milliseconds. |
| `RATE_LIMIT_ENABLED` | Backend | `true` | Optional | Enable per-IP Token Bucket rate limiter. |
| `RATE_LIMIT_CAPACITY` | Backend | `20` | Optional | Maximum token burst capacity per IP. |
| `RATE_LIMIT_REFILL_TOKENS` | Backend | `20` | Optional | Number of tokens refilled per duration window. |
| `RATE_LIMIT_REFILL_DURATION_SECONDS` | Backend | `60` | Optional | Window duration in seconds. |
| `CORS_ALLOWED_ORIGINS` | Backend | `http://localhost:5173` | Optional | Permitted browser origins for CORS preflight. |
| `VITE_API_BASE_URL` | Frontend | `http://localhost:8080` | Optional | Base URL for backend API requests in client build. |

---

## 3. Complete REST API Catalog

### 3.1 Authentication & Identity Domain (`/api/v1/auth`)
| Method | Path | Role / Authorization | Request Body | Response Codes | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | `RegisterRequest` | `201`, `400`, `409` | Registers a new user with BCrypt password hashing. |
| `POST` | `/api/v1/auth/login` | Public | `LoginRequest` | `200`, `400`, `401`, `429` | Authenticates credentials and issues access & refresh tokens. |
| `POST` | `/api/v1/auth/refresh` | Public | `RefreshTokenRequest` | `200`, `401`, `403` | Rotates single-use refresh token and issues new access token. |
| `POST` | `/api/v1/auth/logout` | Authenticated | `RefreshTokenRequest` | `200`, `401` | Revokes the current refresh token session. |
| `GET` | `/api/v1/auth/me` | Authenticated | None | `200`, `401` | Returns profile metadata for the authenticated user. |

### 3.2 Customer Management Domain (`/api/v1/customers`)
| Method | Path | Role / Authorization | Request Body | Response Codes | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/customers` | Authenticated Customer | `CreateCustomerRequest` | `201`, `400`, `409` | Creates linked customer profile for authenticated user. |
| `GET` | `/api/v1/customers/me` | Authenticated Customer | None | `200`, `404` | Retrieves customer profile for authenticated user. |
| `PUT` | `/api/v1/customers/me` | Authenticated Customer | `UpdateCustomerRequest` | `200`, `400`, `404` | Updates contact and address details. |
| `GET` | `/api/v1/admin/customers` | `ROLE_ADMIN` | None (Pageable) | `200`, `403` | Returns paginated list of all banking customers. |
| `GET` | `/api/v1/admin/customers/{id}` | `ROLE_ADMIN` | None | `200`, `403`, `404` | Retrieves detailed customer profile by ID. |

### 3.3 Bank Account Domain (`/api/v1/accounts`)
| Method | Path | Role / Authorization | Request Body | Response Codes | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/accounts` | Authenticated Customer | `CreateAccountRequest` | `201`, `400` | Opens a new `SAVINGS` or `CURRENT` bank account. |
| `GET` | `/api/v1/accounts/my-accounts` | Authenticated Customer | None | `200` | Lists all accounts owned by the authenticated customer. |
| `GET` | `/api/v1/accounts/{accountNumber}` | Customer (Owner) / Admin | None | `200`, `403`, `404` | Retrieves account metadata and current status. |
| `GET` | `/api/v1/accounts/{accountNumber}/balance` | Customer (Owner) / Admin | None | `200`, `403`, `404` | Retrieves available balance representation in INR. |
| `GET` | `/api/v1/accounts/{accountNumber}/reconciliation` | Customer (Owner) / Admin | None | `200`, `403`, `404` | Reconciles stored balance against $\sum Credits - \sum Debits$. |
| `POST` | `/api/v1/admin/accounts/{accountNumber}/freeze` | `ROLE_ADMIN` | `StatusChangeRequest` | `200`, `400`, `403` | Freezes an account with mandatory audit rationale. |
| `POST` | `/api/v1/admin/accounts/{accountNumber}/unfreeze` | `ROLE_ADMIN` | `StatusChangeRequest` | `200`, `400`, `403` | Unfreezes an account with mandatory audit rationale. |

### 3.4 Funds Transfer & Ledger Domain (`/api/v1/transfers`)
| Method | Path | Role / Authorization | Request Body / Headers | Response Codes | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/transfers` | Customer (Sender) / Admin | `TransferRequest`<br/>Header: `Idempotency-Key` | `201`, `400`, `422` | Executes double-entry internal transfer with deterministic locking. |
| `GET` | `/api/v1/transfers/history/{accountNumber}` | Customer (Owner) / Admin | None (Pageable) | `200`, `403`, `404` | Retrieves paginated transaction history for an account. |
| `POST` | `/api/v1/admin/transfers/reverse` | `ROLE_ADMIN` | `ReversalRequest` | `200`, `400`, `403` | Inverts original ledger entries with single-reversal enforcement. |
| `POST` | `/api/v1/admin/simulation/fund-account` | `ROLE_ADMIN` | `SimulatedFundingRequest` | `201`, `400`, `403` | Injects synthetic test funds from Central Reserve into an account. |

### 3.5 Loan Lifecycle Domain (`/api/v1/loans`)
| Method | Path | Role / Authorization | Request Body | Response Codes | Description |
|---|---|---|---|---|---|
| `POST` | `/api/v1/loans/calculate-emi` | Public / Free | `CalculateEmiRequest` | `200`, `400` | Calculates reducing-balance monthly installment quotation. |
| `POST` | `/api/v1/loans/apply` | Authenticated Customer | `LoanApplicationRequest` | `201`, `400`, `422` | Submits loan application and generates installment schedules. |
| `GET` | `/api/v1/loans/my-loans` | Authenticated Customer | None (Pageable) | `200` | Returns all loans for the authenticated customer. |
| `GET` | `/api/v1/loans/{loanNumber}` | Customer (Owner) / Admin | None | `200`, `403`, `404` | Retrieves loan details with scheduled installment records. |
| `POST` | `/api/v1/loans/{loanNumber}/repay` | Authenticated Customer | `RepayLoanRequest` | `200`, `400`, `422` | Makes installment repayment; allocates interest first then principal. |
| `GET` | `/api/v1/admin/loans` | `ROLE_ADMIN` | None (Pageable) | `200`, `403` | Lists all loans across the institution. |
| `POST` | `/api/v1/admin/loans/{loanNumber}/review` | `ROLE_ADMIN` | `LoanReviewRequest` | `200`, `400`, `403` | Approves or rejects loan application. |
| `POST` | `/api/v1/admin/loans/{loanNumber}/disburse` | `ROLE_ADMIN` | None | `200`, `400`, `403` | Disburses loan amount into borrower deposit account via ledger. |

### 3.6 Notification Domain (`/api/v1/notifications`)
| Method | Path | Role / Authorization | Request Body | Response Codes | Description |
|---|---|---|---|---|---|
| `GET` | `/api/v1/notifications` | Authenticated User | None (Pageable, `unreadOnly`) | `200` | Retrieves in-app notification alerts for authenticated user. |
| `GET` | `/api/v1/notifications/unread-count` | Authenticated User | None | `200` | Returns count of unread notifications. |
| `PATCH` | `/api/v1/notifications/{id}/read` | Authenticated User | None | `200`, `403`, `404` | Marks notification as read; enforces ownership isolation. |

### 3.7 Admin Fraud Detection Domain (`/api/v1/admin/fraud`)
| Method | Path | Role / Authorization | Request Body | Response Codes | Description |
|---|---|---|---|---|---|
| `GET` | `/api/v1/admin/fraud/assessments` | `ROLE_ADMIN` | None (Pageable, `decision`, `status`) | `200`, `403` | Retrieves paginated fraud risk evaluations. |
| `POST` | `/api/v1/admin/fraud/assessments/{id}/review` | `ROLE_ADMIN` | `FraudReviewRequest` | `200`, `400`, `403` | Approves or rejects flagged assessment with compliance notes. |

### 3.8 Admin Security Audit Domain (`/api/v1/admin/audit-logs`)
| Method | Path | Role / Authorization | Request Body | Response Codes | Description |
|---|---|---|---|---|---|
| `GET` | `/api/v1/admin/audit-logs` | `ROLE_ADMIN` | None (Pageable) | `200`, `403` | Returns immutable audit events log stream. |

---

## 4. Health, Observability & Documentation Endpoints

| Endpoint | Protocol | Auth Requirement | Description |
|---|---|---|---|
| `/actuator/health` | HTTP GET | Public | Spring Boot Actuator readiness and liveness health status. |
| `/actuator/info` | HTTP GET | Public | Build and version metadata. |
| `/swagger-ui/index.html` | HTTP GET | Public | Interactive OpenAPI 3.0 Swagger UI documentation. |
| `/v3/api-docs` | HTTP GET | Public | Raw OpenAPI 3.0 JSON specification document. |

