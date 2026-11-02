package com.fincore.platform.account.entity;

/**
 * Standard double-entry financial account category.
 * Determines the normal balance sign convention:
 * - ASSET: Normal balance is DEBIT (Debit increases balance, Credit decreases balance).
 * - LIABILITY: Normal balance is CREDIT (Credit increases balance, Debit decreases balance).
 * - EQUITY: Normal balance is CREDIT (Credit increases balance, Debit decreases balance).
 * - REVENUE: Normal balance is CREDIT (Credit increases balance, Debit decreases balance).
 * - EXPENSE: Normal balance is DEBIT (Debit increases balance, Credit decreases balance).
 */
public enum AccountCategory {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE
}

