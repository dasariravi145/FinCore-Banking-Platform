package com.fincore.platform.fraud.rules;

import com.fincore.platform.account.entity.BankAccount;
import java.math.BigDecimal;

public interface FraudRule {
    String getRuleName();
    int evaluate(BankAccount sourceAccount, BankAccount destinationAccount, BigDecimal amount);
    String getExplanation();
}

