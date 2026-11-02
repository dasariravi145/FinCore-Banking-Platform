package com.fincore.platform.fraud.rules;

import com.fincore.platform.account.entity.BankAccount;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class ExcessiveBalanceDepletionRule implements FraudRule {

    private static final BigDecimal MIN_SIGNIFICANT_AMOUNT = BigDecimal.valueOf(100000.00);
    private static final BigDecimal DEPLETION_THRESHOLD_RATIO = BigDecimal.valueOf(0.95);

    @Override
    public String getRuleName() {
        return "EXCESSIVE_BALANCE_DEPLETION";
    }

    @Override
    public int evaluate(BankAccount sourceAccount, BankAccount destinationAccount, BigDecimal amount) {
        if (sourceAccount == null || sourceAccount.getBalance() == null || amount == null) {
            return 0;
        }

        BigDecimal balance = sourceAccount.getBalance();
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        if (amount.compareTo(MIN_SIGNIFICANT_AMOUNT) > 0) {
            BigDecimal ratio = amount.divide(balance, 4, RoundingMode.HALF_EVEN);
            if (ratio.compareTo(DEPLETION_THRESHOLD_RATIO) >= 0) {
                return 25;
            }
        }
        return 0;
    }

    @Override
    public String getExplanation() {
        return "Transfer depletes more than 95% of total account balance on a high amount";
    }
}

