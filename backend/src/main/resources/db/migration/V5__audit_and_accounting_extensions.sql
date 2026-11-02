-- ==============================================================================
-- Migration V5: Accounting Model Extensions & Financial Integrity Fixes
-- FinCore Banking Platform
-- ==============================================================================

-- 1. Add account_category to bank_accounts to distinguish Assets, Liabilities, Equity, Revenue, and Expenses
ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS account_category VARCHAR(30) DEFAULT 'LIABILITY' NOT NULL;

-- 2. Update existing account categories
UPDATE bank_accounts SET account_category = 'EQUITY' WHERE account_type = 'SYSTEM_RESERVE';
UPDATE bank_accounts SET account_category = 'LIABILITY' WHERE account_type IN ('SAVINGS', 'CURRENT');

-- 3. Seed Central Bank Settlement Clearing Account (Asset) to hold offsetting reserve capital
INSERT INTO bank_accounts (id, account_number, account_type, account_category, currency, balance, status)
SELECT '00000000-0000-0000-0000-000000000010', 'ACC-CENTRAL-BANK-SETTLEMENT-001', 'CENTRAL_SETTLEMENT', 'ASSET', 'INR', 100000000.00, 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM bank_accounts WHERE account_number = 'ACC-CENTRAL-BANK-SETTLEMENT-001'
);

-- 4. Balance the Genesis Transaction (TX-GENESIS-RESERVE-001) by adding the balancing DEBIT entry
-- Genesis Transaction now has:
-- Debit:  ACC-CENTRAL-BANK-SETTLEMENT-001 (Asset)   ₹100,000,000.00
-- Credit: ACC-SYSTEM-RESERVE-001          (Equity)  ₹100,000,000.00
-- Total Debits == Total Credits == ₹100,000,000.00
INSERT INTO ledger_entries (id, transaction_id, account_id, entry_type, amount, currency, balance_after, description, created_at)
SELECT '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000010', 'DEBIT', 100000000.00, 'INR', 100000000.00, 'Initial central settlement asset allocation', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM ledger_entries WHERE id = '00000000-0000-0000-0000-000000000011'
);

-- 5. Seed System Loan Portfolio Asset Account (receivable for disbursed loans)
INSERT INTO bank_accounts (id, account_number, account_type, account_category, currency, balance, status)
SELECT '00000000-0000-0000-0000-000000000020', 'ACC-LOAN-PORTFOLIO-001', 'LOAN_PORTFOLIO', 'ASSET', 'INR', 0.00, 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM bank_accounts WHERE account_number = 'ACC-LOAN-PORTFOLIO-001'
);

-- 6. Seed System Interest Income Account (revenue from loan repayments)
INSERT INTO bank_accounts (id, account_number, account_type, account_category, currency, balance, status)
SELECT '00000000-0000-0000-0000-000000000030', 'ACC-INTEREST-INCOME-001', 'INTEREST_INCOME', 'REVENUE', 'INR', 0.00, 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM bank_accounts WHERE account_number = 'ACC-INTEREST-INCOME-001'
);

-- 7. Add Unique Index on reversal_of_transaction_id to enforce single reversal at database level
-- (In SQL-92, H2 and PostgreSQL, UNIQUE constraints allow multiple NULLs but only one instance of each non-NULL ID)
CREATE UNIQUE INDEX IF NOT EXISTS uq_tx_reversal_of_transaction ON transactions (reversal_of_transaction_id);
