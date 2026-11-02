-- ==============================================================================
-- Migration V6: Loans Domain, Transactional Outbox, Notifications & Fraud Detection
-- FinCore Banking Platform
-- ==============================================================================

-- 1. Create Loans table
CREATE TABLE IF NOT EXISTS loans (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    loan_number VARCHAR(32) NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    disbursement_account_id UUID NOT NULL,
    loan_type VARCHAR(30) NOT NULL,
    principal_amount NUMERIC(19, 2) NOT NULL,
    interest_rate NUMERIC(7, 4) NOT NULL,
    term_months INT NOT NULL,
    monthly_emi NUMERIC(19, 2) NOT NULL,
    total_interest_charged NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    outstanding_principal NUMERIC(19, 2) NOT NULL,
    total_principal_paid NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    total_interest_paid NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'APPLIED',
    rejection_reason VARCHAR(255),
    applied_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    approved_by_user_id UUID,
    disbursed_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_loans_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_loans_disbursement_account FOREIGN KEY (disbursement_account_id) REFERENCES bank_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_loans_approved_by FOREIGN KEY (approved_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_loan_principal_positive CHECK (principal_amount > 0.00),
    CONSTRAINT chk_loan_rate_non_negative CHECK (interest_rate >= 0.00),
    CONSTRAINT chk_loan_term_positive CHECK (term_months > 0),
    CONSTRAINT chk_loan_emi_positive CHECK (monthly_emi > 0.00),
    CONSTRAINT chk_loan_outstanding_non_negative CHECK (outstanding_principal >= 0.00)
);

-- 2. Create Loan Repayment Schedules table
CREATE TABLE IF NOT EXISTS loan_repayment_schedules (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    loan_id UUID NOT NULL,
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    emi_amount NUMERIC(19, 2) NOT NULL,
    principal_component NUMERIC(19, 2) NOT NULL,
    interest_component NUMERIC(19, 2) NOT NULL,
    outstanding_balance_after NUMERIC(19, 2) NOT NULL,
    principal_paid NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    interest_paid NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_schedules_loan FOREIGN KEY (loan_id) REFERENCES loans (id) ON DELETE CASCADE,
    CONSTRAINT uq_loan_installment UNIQUE (loan_id, installment_number),
    CONSTRAINT chk_schedule_emi_positive CHECK (emi_amount > 0.00),
    CONSTRAINT chk_schedule_principal_non_negative CHECK (principal_component >= 0.00),
    CONSTRAINT chk_schedule_interest_non_negative CHECK (interest_component >= 0.00)
);

-- 3. Create Loan Payments table
CREATE TABLE IF NOT EXISTS loan_payments (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    loan_id UUID NOT NULL,
    schedule_id UUID,
    transaction_id UUID,
    source_account_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    principal_paid NUMERIC(19, 2) NOT NULL,
    interest_paid NUMERIC(19, 2) NOT NULL,
    payment_reference VARCHAR(64) NOT NULL UNIQUE,
    paid_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_payments_loan FOREIGN KEY (loan_id) REFERENCES loans (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_schedule FOREIGN KEY (schedule_id) REFERENCES loan_repayment_schedules (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_source_account FOREIGN KEY (source_account_id) REFERENCES bank_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT chk_payment_amount_positive CHECK (amount > 0.00)
);

-- 4. Create Notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(150) NOT NULL,
    message VARCHAR(500) NOT NULL,
    type VARCHAR(50) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    read_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 5. Create Transactional Outbox Events table
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE
);

-- 6. Create Fraud Assessments table
CREATE TABLE IF NOT EXISTS fraud_assessments (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    transaction_reference VARCHAR(64),
    source_account_id UUID,
    destination_account_id UUID,
    amount NUMERIC(19, 2) NOT NULL,
    risk_score INT NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    decision VARCHAR(20) NOT NULL,
    triggered_rules TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    reviewed_by_user_id UUID,
    review_notes VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_fraud_source_account FOREIGN KEY (source_account_id) REFERENCES bank_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fraud_dest_account FOREIGN KEY (destination_account_id) REFERENCES bank_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_fraud_reviewed_by FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id) ON DELETE RESTRICT
);

-- 7. High-Performance Indexes
CREATE INDEX IF NOT EXISTS idx_loans_customer_id ON loans (customer_id);
CREATE INDEX IF NOT EXISTS idx_loans_status ON loans (status);
CREATE INDEX IF NOT EXISTS idx_loans_number ON loans (loan_number);
CREATE INDEX IF NOT EXISTS idx_schedules_loan_id ON loan_repayment_schedules (loan_id);
CREATE INDEX IF NOT EXISTS idx_schedules_status ON loan_repayment_schedules (status);
CREATE INDEX IF NOT EXISTS idx_schedules_due_date ON loan_repayment_schedules (due_date);
CREATE INDEX IF NOT EXISTS idx_payments_loan_id ON loan_payments (loan_id);
CREATE INDEX IF NOT EXISTS idx_payments_schedule_id ON loan_payments (schedule_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications (user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_is_read ON notifications (user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_outbox_status_created_at ON outbox_events (status, created_at);
CREATE INDEX IF NOT EXISTS idx_fraud_decision ON fraud_assessments (decision);
CREATE INDEX IF NOT EXISTS idx_fraud_status ON fraud_assessments (status);
CREATE INDEX IF NOT EXISTS idx_fraud_created_at ON fraud_assessments (created_at);

