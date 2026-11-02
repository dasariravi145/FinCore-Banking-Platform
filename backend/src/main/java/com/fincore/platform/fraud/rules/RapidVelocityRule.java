package com.fincore.platform.fraud.rules;

import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.ledger.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Component
public class RapidVelocityRule implements FraudRule {

    private final TransactionRepository transactionRepository;

    public RapidVelocityRule(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public String getRuleName() {
        return "RAPID_TRANSFER_VELOCITY";
    }

    @Override
    public int evaluate(BankAccount sourceAccount, BankAccount destinationAccount, BigDecimal amount) {
        if (sourceAccount == null || sourceAccount.getId() == null) {
            return 0;
        }
        Instant fiveMinutesAgo = Instant.now().minus(Duration.ofMinutes(5));
        long recentTxCount = transactionRepository.countRecentBySourceAccount(sourceAccount.getId(), fiveMinutesAgo);

        if (recentTxCount >= 3) {
            return 40;
        }
        return 0;
    }

    @Override
    public String getExplanation() {
        return "Rapid transfer velocity detected (>= 3 transfers in the last 5 minutes)";
    }
}
