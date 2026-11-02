package com.fincore.platform.account.entity;

public enum AccountType {
    SAVINGS(AccountCategory.LIABILITY),
    CURRENT(AccountCategory.LIABILITY),
    SYSTEM_RESERVE(AccountCategory.EQUITY),
    LOAN_PORTFOLIO(AccountCategory.ASSET),
    CENTRAL_SETTLEMENT(AccountCategory.ASSET),
    INTEREST_INCOME(AccountCategory.REVENUE);

    private final AccountCategory defaultCategory;

    AccountType(AccountCategory defaultCategory) {
        this.defaultCategory = defaultCategory;
    }

    public AccountCategory getDefaultCategory() {
        return defaultCategory;
    }
}
