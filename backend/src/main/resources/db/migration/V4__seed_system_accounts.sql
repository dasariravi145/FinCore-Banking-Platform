-- ==============================================================================
-- Migration V4: Seed System Simulation Funding Account
-- FinCore Banking Platform
-- ==============================================================================

-- Seed Central Simulation Reserve Account with initial synthetic capital (₹100,000,000.00)
INSERT INTO bank_accounts (id, account_number, account_type, currency, balance, status)
SELECT '00000000-0000-0000-0000-000000000001', 'ACC-SYSTEM-RESERVE-001', 'SYSTEM_RESERVE', 'INR', 100000000.00, 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1 FROM bank_accounts WHERE account_number = 'ACC-SYSTEM-RESERVE-001'
);

-- Seed Genesis Transaction for initial synthetic reserve
INSERT INTO transactions (id, transaction_reference, destination_account_id, amount, currency, type, status, description, created_at, completed_at)
SELECT '00000000-0000-0000-0000-000000000002', 'TX-GENESIS-RESERVE-001', '00000000-0000-0000-0000-000000000001', 100000000.00, 'INR', 'SIMULATION_SEED', 'COMPLETED', 'Initial simulation reserve funding', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM transactions WHERE transaction_reference = 'TX-GENESIS-RESERVE-001'
);

-- Seed Ledger Entry for the initial synthetic reserve
INSERT INTO ledger_entries (id, transaction_id, account_id, entry_type, amount, currency, balance_after, description, created_at)
SELECT '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'CREDIT', 100000000.00, 'INR', 100000000.00, 'Initial reserve capital allocation', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM ledger_entries WHERE id = '00000000-0000-0000-0000-000000000003'
);
