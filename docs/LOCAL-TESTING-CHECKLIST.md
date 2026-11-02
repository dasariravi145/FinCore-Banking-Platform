# FinCore Banking Platform — Local Verification & Testing Checklist

**Document Version:** 1.0.0 (Phase 05/06 Final Audit)  
**Classification:** Quality Assurance & Testing Runbook  
**Last Updated:** November 2026  

---

## 1. Safe Local Account Provisioning Runbook

To maintain zero hardcoded production credentials, the FinCore database initializes with empty user tables. For manual local verification, follow this safe, non-destructive provisioning procedure:

### Option A: Via Self-Service API (Customer Account)
1. Register a new user:
   ```powershell
   Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/register" -Method Post -ContentType "application/json" -Body (@{
       firstName = "Aarav"
       lastName = "Sharma"
       email = "aarav.sharma@example.local"
       password = "CustPassword123!"
       phoneNumber = "+919876543210"
   } | ConvertTo-Json)
   ```
2. Authenticate to obtain JWT access token:
   ```powershell
   $login = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -ContentType "application/json" -Body (@{
       email = "aarav.sharma@example.local"
       password = "CustPassword123!"
   } | ConvertTo-Json)
   $customerToken = $login.accessToken
   ```

### Option B: Local Administrative Account Seed (SQL Insertion)
To provision an initial `ROLE_ADMIN` user in your local PostgreSQL or H2 database:
```sql
-- 1. Insert admin user (Password: AdminPassword123! hashed with BCrypt strength 12)
INSERT INTO users (id, email, password, first_name, last_name, phone_number, status, failed_login_attempts, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'admin@fincore.local',
    '$2a$12$e80yq9cK0H4jZlV4O1ZkOeYn6JvR8Y6Pq5m1Z7x.U1k3Y5e8Q9m1K', -- BCrypt for AdminPassword123!
    'System',
    'Administrator',
    '+919876543000',
    'ACTIVE',
    0,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- 2. Map user to ROLE_ADMIN
INSERT INTO user_roles (user_id, role_id)
SELECT 'a0000000-0000-0000-0000-000000000001', id FROM roles WHERE name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;
```

---

## 2. Complete Customer Journey Verification Checklist

| Step # | Action | API Endpoint / UI Action | Expected Result | Pass Criteria |
|:---:|---|---|---|---|
| **C-01** | Register Customer | `POST /api/v1/auth/register`<br/>UI: `/register` | HTTP 201 Created. User record persisted in `users` table with BCrypt hash. | Password hashed; `ROLE_CUSTOMER` assigned. |
| **C-02** | Login | `POST /api/v1/auth/login`<br/>UI: `/login` | HTTP 200 OK. Returns `accessToken` and `refreshToken`. | Token held in React state; user redirected to `/dashboard`. |
| **C-03** | Create Profile | `POST /api/v1/customers`<br/>UI: `/profile` | HTTP 201 Created. `Customer` entity linked 1-to-1 with authenticated user. | Verified KYC status badge displayed. |
| **C-04** | Open Savings Account | `POST /api/v1/accounts` (Type: `SAVINGS`)<br/>UI: `/accounts` Modal | HTTP 201 Created. Account `ACC-XXXXXXXXXXXX` generated. | Balance initialized at ₹0.00; Status is `ACTIVE`. |
| **C-05** | Open Current Account | `POST /api/v1/accounts` (Type: `CURRENT`)<br/>UI: `/accounts` Modal | HTTP 201 Created. Second distinct account generated. | Categorized as Bank `LIABILITY`. |
| **C-06** | Check Balances | `GET /api/v1/accounts/my-accounts`<br/>UI: `/dashboard` | HTTP 200 OK. Returns list of all customer-owned accounts. | Portfolio total displays sum of liquid accounts. |
| **C-07** | Simulated Funding | `POST /api/v1/admin/simulation/fund-account` | HTTP 201 Created. Central Reserve funds deposit account. | Balanced journal: Debit Reserve, Credit Customer Deposit. |
| **C-08** | Transfer Funds | `POST /api/v1/transfers`<br/>UI: `/transfers` | HTTP 201 Created. Transfer receipt returned. | Confirmation dialog verified; `Idempotency-Key` sent. |
| **C-09** | Verify Receipt | UI: Transfer Receipt View | Displays reference `TX-...`, source, destination, fee, timestamp. | Receipt matches transaction details. |
| **C-10** | Verify History | `GET /api/v1/transfers/history/{acc}`<br/>UI: `/accounts/:id` | HTTP 200 OK. Lists credit and debit ledger records. | Accurate pagination and transaction descriptions. |
| **C-11** | Verify Balances | `GET /api/v1/accounts/{acc}/balance` | HTTP 200 OK. Available balance correctly decreased on sender, increased on receiver. | No negative balances. |
| **C-12** | Reconcile Ledger | `GET /api/v1/accounts/{acc}/reconciliation`<br/>UI: `/accounts/:id` | HTTP 200 OK. `isReconciled: true`, `difference: 0.00`. | Stored balance equals $\sum Credits - \sum Debits$. |
| **C-13** | View Notifications | `GET /api/v1/notifications`<br/>UI: `/notifications` | HTTP 200 OK. Displays `TRANSFER_SENT` and `TRANSFER_RECEIVED` alerts. | Unread badge decrements upon marking read. |
| **C-14** | Logout | `POST /api/v1/auth/logout`<br/>UI: Topbar Logout | HTTP 200 OK. Refresh token revoked in database. | In-memory token wiped; user routed to `/login`. |

---

## 3. Complete Loan Lifecycle Verification Checklist

| Step # | Action | API Endpoint / UI Action | Expected Result | Pass Criteria |
|:---:|---|---|---|---|
| **L-01** | EMI Quotation | `POST /api/v1/loans/calculate-emi`<br/>UI: `/loans` Calculator | HTTP 200 OK. Returns monthly EMI, total interest, and total payable. | Matches reducing-balance formula: $EMI = \frac{P \cdot r \cdot (1+r)^n}{(1+r)^n - 1}$. |
| **L-02** | Loan Application | `POST /api/v1/loans/apply`<br/>UI: `/loans` Form | HTTP 201 Created. Loan status is `APPLIED`. | 1..N installment schedules generated in `loan_repayment_schedules`. |
| **L-03** | Unauthorized Approval | `POST /api/v1/admin/loans/{id}/review` (with Customer Token) | HTTP 403 Forbidden. Access Denied. | Ordinary customers cannot approve loans. |
| **L-04** | Admin Approval | `POST /api/v1/admin/loans/{id}/review` (with Admin Token)<br/>UI: `/admin/loans` | HTTP 200 OK. Loan status advances to `APPROVED`. | Audit event `LOAN_APPROVED` logged; outbox event emitted. |
| **L-05** | Loan Disbursement | `POST /api/v1/admin/loans/{id}/disburse`<br/>UI: `/admin/loans` | HTTP 200 OK. Loan status advances to `ACTIVE`. | Balanced double-entry journal posted: Debit Loan Portfolio Asset, Credit Customer Account. |
| **L-06** | Inspect Amortization | `GET /api/v1/loans/{id}`<br/>UI: `/loans/:id` | HTTP 200 OK. Full table of installments displayed. | Principal + Interest components sum to exact EMI. |
| **L-07** | Partial Repayment | `POST /api/v1/loans/{id}/repay`<br/>UI: `/loans/:id` Modal | HTTP 200 OK. Repayment receipt generated. | Multi-leg journal: Debit Customer Deposit, Credit Loan Portfolio Asset, Credit Interest Income. |
| **L-08** | Full Repayment & Closure | `POST /api/v1/loans/{id}/repay` (Remaining Balance) | HTTP 200 OK. Outstanding principal reaches ₹0.00. | Loan status advances to `CLOSED`. |
| **L-09** | Duplicate Protection | Replay identical repayment request with same idempotency key | HTTP 200 OK. Returns cached receipt without double-debiting. | Customer balance debited only once. |

---

## 4. Administrative Oversight & Compliance Checklist

| Step # | Action | API Endpoint / UI Action | Expected Result | Pass Criteria |
|:---:|---|---|---|---|
| **A-01** | Executive Hub | `GET /api/v1/admin/...`<br/>UI: `/admin` | Displays total deposits, active loan exposure, and pending review counts. | Metrics reflect live aggregated database records. |
| **A-02** | Freeze Account | `POST /api/v1/admin/accounts/{acc}/freeze`<br/>UI: `/admin/accounts` Modal | HTTP 200 OK. Account status changes to `FROZEN`. | Mandatory audit rationale captured; subsequent transfers blocked. |
| **A-03** | Unfreeze Account | `POST /api/v1/admin/accounts/{acc}/unfreeze`<br/>UI: `/admin/accounts` Modal | HTTP 200 OK. Account status returns to `ACTIVE`. | Transfers permitted again; audit log recorded. |
| **A-04** | Transaction Reversal | `POST /api/v1/admin/transfers/reverse`<br/>UI: `/admin/transfers` Modal | HTTP 200 OK. Inverted balanced journal posted. | Original transaction marked `REVERSED`. Second reversal strictly rejected. |
| **A-05** | Fraud Review Adjudication | `POST /api/v1/admin/fraud/assessments/{id}/review`<br/>UI: `/admin/fraud` Modal | HTTP 200 OK. Assessment marked `APPROVED` or `REJECTED`. | Review notes persisted; does not auto-execute blocked transfer. |
| **A-06** | Security Audit Inspection | `GET /api/v1/admin/audit-logs`<br/>UI: `/admin/audit` | HTTP 200 OK. Immutable log table displayed. | Displays principal, client IP, action, timestamp, and correlation ID. |

