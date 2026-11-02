package com.fincore.platform.ledger;

import com.fincore.platform.account.entity.AccountType;
import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.account.repository.BankAccountRepository;
import com.fincore.platform.common.error.InsufficientFundsException;
import com.fincore.platform.common.error.InvalidTransferException;
import com.fincore.platform.ledger.dto.ReconciliationResult;
import com.fincore.platform.ledger.entity.LedgerEntry;
import com.fincore.platform.ledger.entity.LedgerEntryType;
import com.fincore.platform.ledger.entity.Transaction;
import com.fincore.platform.ledger.entity.TransactionType;
import com.fincore.platform.ledger.repository.LedgerEntryRepository;
import com.fincore.platform.ledger.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @InjectMocks
    private LedgerService ledgerService;

    private BankAccount debitAccount;
    private BankAccount creditAccount;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        debitAccount = new BankAccount(null, "ACC-SENDER-001", AccountType.CURRENT, "INR");
        debitAccount.setId(UUID.randomUUID());
        debitAccount.setBalance(BigDecimal.valueOf(10000.00));

        creditAccount = new BankAccount(null, "ACC-RECEIVER-001", AccountType.SAVINGS, "INR");
        creditAccount.setId(UUID.randomUUID());
        creditAccount.setBalance(BigDecimal.valueOf(2000.00));

        transaction = new Transaction(
                "TX-TEST-001", debitAccount, creditAccount,
                BigDecimal.valueOf(1500.00), "INR", TransactionType.INTERNAL_TRANSFER,
                "idem-key-1", "Test Transfer", null
        );
        transaction.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Post balanced double-entry ledger entries updates balances and preserves debit-credit equality")
    void testPostDoubleEntry_Success() {
        BigDecimal transferAmount = BigDecimal.valueOf(1500.00);

        ledgerService.postDoubleEntry(transaction, debitAccount, creditAccount, transferAmount, "Consulting Fee");

        // Debit account should decrease by 1500.00 -> 8500.00
        assertThat(debitAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(8500.00));

        // Credit account should increase by 1500.00 -> 3500.00
        assertThat(creditAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(3500.00));

        // Capture saved ledger entries
        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());

        List<LedgerEntry> entries = entryCaptor.getAllValues();
        assertThat(entries).hasSize(2);

        LedgerEntry debitEntry = entries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.DEBIT)
                .findFirst().orElseThrow();
        assertThat(debitEntry.getAmount()).isEqualByComparingTo(transferAmount);
        assertThat(debitEntry.getBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(8500.00));
        assertThat(debitEntry.getAccount().getAccountNumber()).isEqualTo("ACC-SENDER-001");

        LedgerEntry creditEntry = entries.stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.CREDIT)
                .findFirst().orElseThrow();
        assertThat(creditEntry.getAmount()).isEqualByComparingTo(transferAmount);
        assertThat(creditEntry.getBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(3500.00));
        assertThat(creditEntry.getAccount().getAccountNumber()).isEqualTo("ACC-RECEIVER-001");

        // Mathematical equality: debit amount == credit amount
        assertThat(debitEntry.getAmount()).isEqualByComparingTo(creditEntry.getAmount());
    }

    @Test
    @DisplayName("Post double-entry fails when debit account has insufficient funds")
    void testPostDoubleEntry_InsufficientFunds() {
        BigDecimal transferAmount = BigDecimal.valueOf(50000.00); // balance is only 10000.00

        assertThatThrownBy(() -> ledgerService.postDoubleEntry(transaction, debitAccount, creditAccount, transferAmount, "Large transfer"))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("insufficient funds");

        // Verify no ledger entries saved
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Post double-entry fails when transfer amount is negative or zero")
    void testPostDoubleEntry_NegativeOrZeroRejected() {
        assertThatThrownBy(() -> ledgerService.postDoubleEntry(transaction, debitAccount, creditAccount, BigDecimal.ZERO, "Zero"))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessageContaining("strictly greater than zero");

        assertThatThrownBy(() -> ledgerService.postDoubleEntry(transaction, debitAccount, creditAccount, BigDecimal.valueOf(-50.00), "Negative"))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessageContaining("strictly greater than zero");
    }

    @Test
    @DisplayName("Reconcile account balance successfully against ledger entries")
    void testReconcileAccountBalance_Success() {
        when(ledgerEntryRepository.calculateReconciledBalance(debitAccount.getId()))
                .thenReturn(BigDecimal.valueOf(10000.00));

        ReconciliationResult result = ledgerService.reconcileAccountBalance(debitAccount);

        assertThat(result.reconciled()).isTrue();
        assertThat(result.difference()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.accountBalance()).isEqualByComparingTo(BigDecimal.valueOf(10000.00));
        assertThat(result.ledgerBalance()).isEqualByComparingTo(BigDecimal.valueOf(10000.00));
    }

    @Test
    @DisplayName("Verify transaction balance returns true when debits equal credits")
    void testVerifyTransactionBalance_Success() {
        when(ledgerEntryRepository.sumDebitsByTransactionId(transaction.getId()))
                .thenReturn(BigDecimal.valueOf(1500.00));
        when(ledgerEntryRepository.sumCreditsByTransactionId(transaction.getId()))
                .thenReturn(BigDecimal.valueOf(1500.00));

        boolean balanced = ledgerService.verifyTransactionBalance(transaction.getId());

        assertThat(balanced).isTrue();
    }
}
