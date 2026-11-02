package com.fincore.platform.ledger.service;

import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.account.repository.BankAccountRepository;
import com.fincore.platform.common.error.ResourceNotFoundException;
import com.fincore.platform.common.error.UnauthorizedAccountAccessException;
import com.fincore.platform.ledger.dto.ReconciliationResult;
import com.fincore.platform.ledger.entity.Transaction;
import com.fincore.platform.ledger.repository.TransactionRepository;
import com.fincore.platform.transfer.dto.TransactionSummaryResponse;
import com.fincore.platform.transfer.dto.TransferReceiptResponse;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final BankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;

    public TransactionService(TransactionRepository transactionRepository,
                              BankAccountRepository bankAccountRepository,
                              UserRepository userRepository,
                              LedgerService ledgerService) {
        this.transactionRepository = transactionRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.userRepository = userRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional(readOnly = true)
    public TransferReceiptResponse getReceiptByReference(String transactionReference, UUID userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Transaction tx = transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionReference));

        boolean isAdmin = currentUser.getRoles().stream().anyMatch(r -> RoleName.ROLE_ADMIN.equals(r.getName()));
        if (!isAdmin) {
            boolean userOwnsSource = tx.getSourceAccount() != null &&
                    tx.getSourceAccount().getCustomer() != null &&
                    tx.getSourceAccount().getCustomer().getUser().getId().equals(currentUser.getId());

            boolean userOwnsDest = tx.getDestinationAccount() != null &&
                    tx.getDestinationAccount().getCustomer() != null &&
                    tx.getDestinationAccount().getCustomer().getUser().getId().equals(currentUser.getId());

            if (!userOwnsSource && !userOwnsDest) {
                throw new UnauthorizedAccountAccessException("User is not authorized to view transaction: " + transactionReference);
            }
        }

        return toReceiptResponse(tx);
    }

    @Transactional(readOnly = true)
    public Page<TransactionSummaryResponse> getAccountTransactions(
            String accountNumber, Instant from, Instant to, Pageable pageable, UUID userId) {

        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        BankAccount account = bankAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        boolean isAdmin = currentUser.getRoles().stream().anyMatch(r -> RoleName.ROLE_ADMIN.equals(r.getName()));
        if (!isAdmin) {
            if (account.getCustomer() == null || !account.getCustomer().getUser().getId().equals(currentUser.getId())) {
                throw new UnauthorizedAccountAccessException("User does not have authorization to view account " + accountNumber);
            }
        }

        Instant startDate = from != null ? from : Instant.now().minus(365, ChronoUnit.DAYS);
        Instant endDate = to != null ? to : Instant.now().plus(1, ChronoUnit.DAYS);

        Page<Transaction> transactions = transactionRepository.findByAccountNumberAndDateRange(
                accountNumber, startDate, endDate, pageable);

        return transactions.map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<TransactionSummaryResponse> getAllTransactions(Pageable pageable) {
        return transactionRepository.findAll(pageable).map(this::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public ReconciliationResult reconcileAccount(String accountNumber, UUID userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        BankAccount account = bankAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        boolean isAdmin = currentUser.getRoles().stream().anyMatch(r -> RoleName.ROLE_ADMIN.equals(r.getName()));
        if (!isAdmin) {
            if (account.getCustomer() == null || !account.getCustomer().getUser().getId().equals(currentUser.getId())) {
                throw new UnauthorizedAccountAccessException("User does not have authorization to inspect account " + accountNumber);
            }
        }

        return ledgerService.reconcileAccountBalance(account);
    }

    private TransferReceiptResponse toReceiptResponse(Transaction tx) {
        String sourceNum = tx.getSourceAccount() != null ? tx.getSourceAccount().getAccountNumber() : "NONE";
        String destNum = tx.getDestinationAccount() != null ? tx.getDestinationAccount().getAccountNumber() : "NONE";

        return new TransferReceiptResponse(
                tx.getTransactionReference(),
                sourceNum,
                destNum,
                tx.getAmount(),
                tx.getCurrency(),
                tx.getType().name(),
                tx.getStatus().name(),
                tx.getCompletedAt() != null ? tx.getCompletedAt() : tx.getCreatedAt(),
                tx.getDescription(),
                tx.getIdempotencyKey()
        );
    }

    private TransactionSummaryResponse toSummaryResponse(Transaction tx) {
        String sourceNum = tx.getSourceAccount() != null ? tx.getSourceAccount().getAccountNumber() : "SYSTEM";
        String destNum = tx.getDestinationAccount() != null ? tx.getDestinationAccount().getAccountNumber() : "SYSTEM";

        return new TransactionSummaryResponse(
                tx.getTransactionReference(),
                sourceNum,
                destNum,
                tx.getAmount(),
                tx.getCurrency(),
                tx.getType().name(),
                tx.getStatus().name(),
                tx.getDescription(),
                tx.getCreatedAt(),
                tx.getCompletedAt()
        );
    }
}
