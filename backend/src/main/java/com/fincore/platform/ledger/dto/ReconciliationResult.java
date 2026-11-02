package com.fincore.platform.ledger.dto;

import java.math.BigDecimal;

public record ReconciliationResult(
        String accountNumber,
        BigDecimal accountBalance,
        BigDecimal ledgerBalance,
        boolean reconciled,
        BigDecimal difference
) {}

