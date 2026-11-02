package com.fincore.platform.fraud.rules;

import com.fincore.platform.account.entity.BankAccount;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class HighValueTransferRule implements FraudRule {

    public static final BigDecimal THRESHOLD = BigDecimal.valueOf(500000.00);

    @Override
    public String getRuleName() {
        return "HIGH_VALUE_TRANSFER";
    }

    @Override
    public int evaluate(BankAccount sourceAccount, BankAccount destinationAccount, BigDecimal amount) {
        if (amount != null && amount.compareTo(THRESHOLD) > 0) {
            return 50;
        }
        return 0;
    }

    @Override
    public String getExplanation() {
        return "Transfer amount exceeds high-value threshold (₹500,000.00)";
    }
}

