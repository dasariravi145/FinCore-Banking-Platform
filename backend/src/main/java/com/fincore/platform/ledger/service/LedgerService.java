package com.fincore.platform.ledger.service;

import com.fincore.platform.account.entity.AccountCategory;
import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.account.repository.BankAccountRepository;
import com.fincore.platform.common.error.InsufficientFundsException;
import com.fincore.platform.common.error.InvalidTransferException;
import com.fincore.platform.ledger.dto.ReconciliationResult;
import com.fincore.platform.ledger.entity.LedgerEntry;
import com.fincore.platform.ledger.entity.LedgerEntryType;
import com.fincore.platform.ledger.entity.Transaction;
import com.fincore.platform.ledger.repository.LedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final BankAccountRepository bankAccountRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository,
                         BankAccountRepository bankAccountRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    /**
     * Posts balanced pairwise double-entry ledger entries for a transaction.
     * Enforces equal debits and credits and updates account balances atomically according to account category rules.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postDoubleEntry(Transaction transaction, BankAccount debitAccount, BankAccount creditAccount,
                                BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be strictly greater than zero");
        }

        BigDecimal scaledAmount = amount.setScale(2, RoundingMode.HALF_EVEN);

        // 1. Debit leg: Apply debit according to account category normal balance
        BigDecimal debitBefore = debitAccount.getBalance();
        BigDecimal debitAfter;
        if (isDebitNormal(debitAccount.getAccountCategory())) {
            // Assets / Expenses: Debit increases balance
            debitAfter = debitBefore.add(scaledAmount).setScale(2, RoundingMode.HALF_EVEN);
        } else {
            // Liabilities / Equity / Revenue: Debit decreases balance
            if (debitBefore.compareTo(scaledAmount) < 0) {
                throw new InsufficientFundsException("Account " + debitAccount.getAccountNumber() + " has insufficient funds");
            }
            debitAfter = debitBefore.subtract(scaledAmount).setScale(2, RoundingMode.HALF_EVEN);
        }
        debitAccount.setBalance(debitAfter);
        bankAccountRepository.save(debitAccount);

        LedgerEntry debitEntry = new LedgerEntry(
                transaction,
                debitAccount,
                LedgerEntryType.DEBIT,
                scaledAmount,
                debitAccount.getCurrency(),
                debitAfter,
                description != null ? description : "Debit posting for TX " + transaction.getTransactionReference()
        );
        ledgerEntryRepository.save(debitEntry);

        // 2. Credit leg: Apply credit according to account category normal balance
        BigDecimal creditBefore = creditAccount.getBalance();
        BigDecimal creditAfter;
        if (isDebitNormal(creditAccount.getAccountCategory())) {
            // Assets / Expenses: Credit decreases balance
            creditAfter = creditBefore.subtract(scaledAmount).setScale(2, RoundingMode.HALF_EVEN);
        } else {
            // Liabilities / Equity / Revenue: Credit increases balance
            creditAfter = creditBefore.add(scaledAmount).setScale(2, RoundingMode.HALF_EVEN);
        }
        creditAccount.setBalance(creditAfter);
        bankAccountRepository.save(creditAccount);

        LedgerEntry creditEntry = new LedgerEntry(
                transaction,
                creditAccount,
                LedgerEntryType.CREDIT,
                scaledAmount,
                creditAccount.getCurrency(),
                creditAfter,
                description != null ? description : "Credit posting for TX " + transaction.getTransactionReference()
        );
        ledgerEntryRepository.save(creditEntry);

        log.info("Posted double-entry for TX [{}]: Debited {} from [{}], Credited {} to [{}]",
                transaction.getTransactionReference(), scaledAmount, debitAccount.getAccountNumber(),
                creditAccount.getAccountNumber());
    }

    /**
     * Posts loan repayment multi-leg entries:
     * - Debit: Customer Deposit Account (Liability decreases) = principal + interest
     * - Credit: Loan Portfolio Account (Asset decreases) = principal
     * - Credit: Interest Income Account (Revenue increases) = interest
     * Total Debits == Total Credits == Total Payment
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void postLoanRepaymentJournal(Transaction transaction, BankAccount borrowerAccount,
                                         BankAccount loanAssetAccount, BankAccount interestIncomeAccount,
                                         BigDecimal principalPaid, BigDecimal interestPaid, String description) {
        BigDecimal totalPayment = principalPaid.add(interestPaid).setScale(2, RoundingMode.HALF_EVEN);
        if (totalPayment.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Loan repayment total must be strictly positive");
        }

        // 1. Debit borrower account
        BigDecimal borrowerBefore = borrowerAccount.getBalance();
        if (borrowerBefore.compareTo(totalPayment) < 0) {
            throw new InsufficientFundsException("Account " + borrowerAccount.getAccountNumber() + " has insufficient funds for loan repayment");
        }
        BigDecimal borrowerAfter = borrowerBefore.subtract(totalPayment).setScale(2, RoundingMode.HALF_EVEN);
        borrowerAccount.setBalance(borrowerAfter);
        bankAccountRepository.save(borrowerAccount);

        LedgerEntry debitEntry = new LedgerEntry(
                transaction,
                borrowerAccount,
                LedgerEntryType.DEBIT,
                totalPayment,
                borrowerAccount.getCurrency(),
                borrowerAfter,
                description != null ? description : "Repayment debit from borrower " + borrowerAccount.getAccountNumber()
        );
        ledgerEntryRepository.save(debitEntry);

        // 2. Credit Loan Asset account (principal portion)
        if (principalPaid.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal principalScaled = principalPaid.setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal loanAssetBefore = loanAssetAccount.getBalance();
            BigDecimal loanAssetAfter = loanAssetBefore.subtract(principalScaled).setScale(2, RoundingMode.HALF_EVEN);
            loanAssetAccount.setBalance(loanAssetAfter);
            bankAccountRepository.save(loanAssetAccount);

            LedgerEntry principalCredit = new LedgerEntry(
                    transaction,
                    loanAssetAccount,
                    LedgerEntryType.CREDIT,
                    principalScaled,
                    loanAssetAccount.getCurrency(),
                    loanAssetAfter,
                    "Loan principal repayment credit"
            );
            ledgerEntryRepository.save(principalCredit);
        }

        // 3. Credit Interest Income account (interest portion)
        if (interestPaid.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal interestScaled = interestPaid.setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal interestBefore = interestIncomeAccount.getBalance();
            BigDecimal interestAfter = interestBefore.add(interestScaled).setScale(2, RoundingMode.HALF_EVEN);
            interestIncomeAccount.setBalance(interestAfter);
            bankAccountRepository.save(interestIncomeAccount);

            LedgerEntry interestCredit = new LedgerEntry(
                    transaction,
                    interestIncomeAccount,
                    LedgerEntryType.CREDIT,
                    interestScaled,
                    interestIncomeAccount.getCurrency(),
                    interestAfter,
                    "Loan interest income credit"
            );
            ledgerEntryRepository.save(interestCredit);
        }

        log.info("Posted loan repayment journal for TX [{}]: Debited {} from [{}], Credited Principal {} to [{}], Credited Interest {} to [{}]",
                transaction.getTransactionReference(), totalPayment, borrowerAccount.getAccountNumber(),
                principalPaid, loanAssetAccount.getAccountNumber(), interestPaid, interestIncomeAccount.getAccountNumber());
    }

    private boolean isDebitNormal(AccountCategory category) {
        return category == AccountCategory.ASSET || category == AccountCategory.EXPENSE;
    }

    /**
     * Reconciles an account balance with its aggregate immutable ledger history.
     */
    @Transactional(readOnly = true)
    public ReconciliationResult reconcileAccountBalance(BankAccount account) {
        BigDecimal ledgerBalance = ledgerEntryRepository.calculateReconciledBalance(account.getId());
        BigDecimal currentBalance = account.getBalance();
        BigDecimal difference = currentBalance.subtract(ledgerBalance).abs();
        boolean isReconciled = difference.compareTo(BigDecimal.ZERO) == 0;

        return new ReconciliationResult(
                account.getAccountNumber(),
                currentBalance,
                ledgerBalance,
                isReconciled,
                difference
        );
    }

    /**
     * Verifies that total debits equal total credits for a transaction.
     */
    @Transactional(readOnly = true)
    public boolean verifyTransactionBalance(UUID transactionId) {
        BigDecimal totalDebits = ledgerEntryRepository.sumDebitsByTransactionId(transactionId);
        BigDecimal totalCredits = ledgerEntryRepository.sumCreditsByTransactionId(transactionId);
        return totalDebits.compareTo(totalCredits) == 0 && totalDebits.compareTo(BigDecimal.ZERO) > 0;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getLedgerEntriesForTransaction(UUID transactionId) {
        return ledgerEntryRepository.findByTransaction_Id(transactionId);
    }
}
