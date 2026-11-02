-- ==============================================================================
-- Migration V3: Banking Core (Customers, Accounts, Transactions, Ledger, Idempotency)
-- FinCore Banking Platform
-- ==============================================================================

-- 1. Create Customers table
CREATE TABLE IF NOT EXISTS customers (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    customer_number VARCHAR(32) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(30),
    date_of_birth DATE,
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100) DEFAULT 'India',
    national_id VARCHAR(50),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
);

-- 2. Create Bank Accounts table
CREATE TABLE IF NOT EXISTS bank_accounts (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    customer_id UUID,
    account_number VARCHAR(34) NOT NULL UNIQUE,
    account_type VARCHAR(30) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_bank_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT chk_positive_balance CHECK (balance >= 0.00)
);

-- 3. Create Transactions table
CREATE TABLE IF NOT EXISTS transactions (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    transaction_reference VARCHAR(64) NOT NULL UNIQUE,
    source_account_id UUID,
    destination_account_id UUID,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(128),
    description VARCHAR(255),
    reversal_of_transaction_id UUID,
    initiated_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_tx_source FOREIGN KEY (source_account_id) REFERENCES bank_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tx_dest FOREIGN KEY (destination_account_id) REFERENCES bank_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tx_reversal FOREIGN KEY (reversal_of_transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_tx_initiated_by FOREIGN KEY (initiated_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_tx_amount_positive CHECK (amount > 0.00)
);

-- 4. Create Ledger Entries table (Append-only Double-Entry Journal)
CREATE TABLE IF NOT EXISTS ledger_entries (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    transaction_id UUID NOT NULL,
    account_id UUID NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    balance_after NUMERIC(19, 2) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_ledger_tx FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ledger_account FOREIGN KEY (account_id) REFERENCES bank_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT chk_ledger_amount_positive CHECK (amount > 0.00),
    CONSTRAINT chk_ledger_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT'))
);

-- 5. Create Idempotency Records table
CREATE TABLE IF NOT EXISTS idempotency_records (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_payload TEXT,
    transaction_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_idempotency_tx FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL
);

-- Indexes for high-throughput queries and auditing
CREATE INDEX IF NOT EXISTS idx_customers_user_id ON customers (user_id);
CREATE INDEX IF NOT EXISTS idx_customers_number ON customers (customer_number);
CREATE INDEX IF NOT EXISTS idx_accounts_customer_id ON bank_accounts (customer_id);
CREATE INDEX IF NOT EXISTS idx_accounts_number ON bank_accounts (account_number);
CREATE INDEX IF NOT EXISTS idx_accounts_status ON bank_accounts (status);
CREATE INDEX IF NOT EXISTS idx_tx_reference ON transactions (transaction_reference);
CREATE INDEX IF NOT EXISTS idx_tx_source ON transactions (source_account_id);
CREATE INDEX IF NOT EXISTS idx_tx_dest ON transactions (destination_account_id);
CREATE INDEX IF NOT EXISTS idx_tx_created_at ON transactions (created_at);
CREATE INDEX IF NOT EXISTS idx_ledger_tx ON ledger_entries (transaction_id);
CREATE INDEX IF NOT EXISTS idx_ledger_account ON ledger_entries (account_id);
CREATE INDEX IF NOT EXISTS idx_ledger_created_at ON ledger_entries (created_at);
CREATE INDEX IF NOT EXISTS idx_idempotency_key ON idempotency_records (idempotency_key);

