package com.fincore.platform.transfer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.platform.account.entity.AccountStatus;
import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.account.repository.BankAccountRepository;
import com.fincore.platform.common.audit.AuditEventType;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.*;
import com.fincore.platform.fraud.service.FraudDetectionService;
import com.fincore.platform.notification.entity.NotificationType;
import com.fincore.platform.notification.service.NotificationService;
import com.fincore.platform.notification.service.OutboxEventService;
import com.fincore.platform.ledger.entity.Transaction;
import com.fincore.platform.ledger.entity.TransactionStatus;
import com.fincore.platform.ledger.entity.TransactionType;
import com.fincore.platform.ledger.repository.TransactionRepository;
import com.fincore.platform.ledger.service.LedgerService;
import com.fincore.platform.transfer.dto.*;
import com.fincore.platform.transfer.entity.IdempotencyRecord;
import com.fincore.platform.transfer.entity.IdempotencyStatus;
import com.fincore.platform.transfer.repository.IdempotencyRecordRepository;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);
    public static final String SYSTEM_RESERVE_ACCOUNT = "ACC-SYSTEM-RESERVE-001";

    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final SecurityAuditService auditService;
    private final ObjectMapper objectMapper;
    private final FraudDetectionService fraudDetectionService;
    private final NotificationService notificationService;
    private final OutboxEventService outboxEventService;

    public TransferService(BankAccountRepository bankAccountRepository,
                           TransactionRepository transactionRepository,
                           IdempotencyRecordRepository idempotencyRecordRepository,
                           UserRepository userRepository,
                           LedgerService ledgerService,
                           SecurityAuditService auditService,
                           ObjectMapper objectMapper,
                           FraudDetectionService fraudDetectionService,
                           NotificationService notificationService,
                           OutboxEventService outboxEventService) {
        this.bankAccountRepository = bankAccountRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.userRepository = userRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.fraudDetectionService = fraudDetectionService;
        this.notificationService = notificationService;
        this.outboxEventService = outboxEventService;
    }

    /**
     * Executes internal transfer between two bank accounts with deterministic locking,
     * balance validation, double-entry ledger posting, and SHA-256 idempotency protection.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferReceiptResponse transferFunds(TransferRequest request, String idempotencyKey, UUID userId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidTransferException("Idempotency-Key header is required for transfers");
        }

        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String fingerprint = computeFingerprint(request);

        // Check for existing idempotency record
        Optional<IdempotencyRecord> existingOpt = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey.trim());
        if (existingOpt.isPresent()) {
            IdempotencyRecord existing = existingOpt.get();
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyPayloadMismatchException(
                        "Idempotency key '" + idempotencyKey + "' has already been used with different request parameters"
                );
            }
            if (existing.getStatus() == IdempotencyStatus.COMPLETED && existing.getResponsePayload() != null) {
                log.info("Idempotent request detected for key [{}]. Returning cached receipt.", idempotencyKey);
                try {
                    return objectMapper.readValue(existing.getResponsePayload(), TransferReceiptResponse.class);
                } catch (JsonProcessingException e) {
                    log.error("Failed to deserialize cached response payload for idempotency key [{}]", idempotencyKey, e);
                }
            } else if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
                throw new InvalidTransferException("A transfer with this idempotency key is currently processing. Please retry shortly.");
            }
        }

        // Validate self-transfer
        if (request.sourceAccountNumber().trim().equalsIgnoreCase(request.destinationAccountNumber().trim())) {
            throw new InvalidTransferException("Cannot transfer funds to the same account");
        }

        BigDecimal scaledAmount = request.amount().setScale(2, RoundingMode.HALF_EVEN);
        if (scaledAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Transfer amount must be strictly greater than zero");
        }

        String transferCurrency = request.currency() != null ? request.currency().toUpperCase() : "INR";

        // Lookup accounts
        BankAccount source = bankAccountRepository.findByAccountNumber(request.sourceAccountNumber().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found: " + request.sourceAccountNumber()));

        BankAccount destination = bankAccountRepository.findByAccountNumber(request.destinationAccountNumber().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + request.destinationAccountNumber()));

        // Ownership enforcement for ordinary customers
        boolean isAdmin = currentUser.getRoles().stream().anyMatch(r -> RoleName.ROLE_ADMIN.equals(r.getName()));
        if (!isAdmin) {
            if (source.getCustomer() == null || !source.getCustomer().getUser().getId().equals(currentUser.getId())) {
                throw new UnauthorizedAccountAccessException("User does not have authorization to transfer from account " + source.getAccountNumber());
            }
        }

        // Account status validations
        validateAccountStatusForTransfer(source, "Source");
        validateAccountStatusForTransfer(destination, "Destination");

        // Currency validation
        if (!source.getCurrency().equalsIgnoreCase(transferCurrency) || !destination.getCurrency().equalsIgnoreCase(transferCurrency)) {
            throw new InvalidTransferException("Account currency and transfer currency must match: " + transferCurrency);
        }

        // Deterministic lock acquisition to prevent deadlocks (Lock lower UUID first)
        UUID firstId = source.getId().compareTo(destination.getId()) < 0 ? source.getId() : destination.getId();
        UUID secondId = source.getId().compareTo(destination.getId()) < 0 ? destination.getId() : source.getId();

        BankAccount lockedFirst = bankAccountRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Account lock acquisition failed: " + firstId));
        BankAccount lockedSecond = bankAccountRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Account lock acquisition failed: " + secondId));

        BankAccount lockedSource = source.getId().equals(firstId) ? lockedFirst : lockedSecond;
        BankAccount lockedDest = destination.getId().equals(firstId) ? lockedFirst : lockedSecond;

        // Re-validate status and balance under fresh lock
        validateAccountStatusForTransfer(lockedSource, "Source");
        validateAccountStatusForTransfer(lockedDest, "Destination");

        if (lockedSource.getBalance().compareTo(scaledAmount) < 0) {
            throw new InsufficientFundsException("Account " + lockedSource.getAccountNumber() + " has insufficient available balance");
        }

        // Create initial idempotency record in PROCESSING if not already present
        IdempotencyRecord record = existingOpt.orElseGet(() ->
                idempotencyRecordRepository.save(new IdempotencyRecord(
                        idempotencyKey.trim(),
                        currentUser,
                        fingerprint,
                        IdempotencyStatus.PROCESSING,
                        Instant.now().plus(Duration.ofHours(24))
                ))
        );

        // Create transaction record
        String txRef = "TX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        // Evaluate rule-based fraud detection before executing financial ledger movement
        fraudDetectionService.evaluateAndEnforce(lockedSource, lockedDest, scaledAmount, txRef);

        Transaction tx = new Transaction(
                txRef,
                lockedSource,
                lockedDest,
                scaledAmount,
                transferCurrency,
                TransactionType.INTERNAL_TRANSFER,
                idempotencyKey,
                request.description(),
                currentUser
        );
        tx = transactionRepository.save(tx);

        // Post balanced double-entry ledger entries and update balances
        ledgerService.postDoubleEntry(tx, lockedSource, lockedDest, scaledAmount, request.description());

        // Mark transaction as completed
        tx.markCompleted();
        tx = transactionRepository.save(tx);

        TransferReceiptResponse receipt = new TransferReceiptResponse(
                tx.getTransactionReference(),
                lockedSource.getAccountNumber(),
                lockedDest.getAccountNumber(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getType().name(),
                tx.getStatus().name(),
                tx.getCompletedAt(),
                tx.getDescription(),
                idempotencyKey
        );

        // Update idempotency record to COMPLETED with cached response
        try {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setTransaction(tx);
            record.setResponsePayload(objectMapper.writeValueAsString(receipt));
            idempotencyRecordRepository.save(record);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize receipt for idempotency key [{}]", idempotencyKey, e);
        }

        auditService.recordEvent(
                AuditEventType.TRANSFER_COMPLETED,
                currentUser.getEmail(),
                "127.0.0.1",
                "FinCore-Backend",
                "SUCCESS",
                "Transfer completed: " + scaledAmount + " " + transferCurrency + " from " +
                        lockedSource.getAccountNumber() + " to " + lockedDest.getAccountNumber() +
                        " (Ref: " + txRef + ")"
        );

        // Record event in transactional outbox for reliable asynchronous delivery
        outboxEventService.publishEvent("TRANSFER", tx.getId().toString(), "TRANSFER_COMPLETED", receipt);

        // Generate in-app notifications
        if (lockedDest.getCustomer() != null && lockedDest.getCustomer().getUser() != null) {
            notificationService.createNotification(
                    lockedDest.getCustomer().getUser(),
                    "Transfer Received",
                    "You have received " + scaledAmount + " " + transferCurrency + " from account " + lockedSource.getAccountNumber(),
                    NotificationType.TRANSFER_RECEIVED
            );
        }
        if (lockedSource.getCustomer() != null && lockedSource.getCustomer().getUser() != null) {
            notificationService.createNotification(
                    lockedSource.getCustomer().getUser(),
                    "Transfer Sent",
                    "You transferred " + scaledAmount + " " + transferCurrency + " to account " + lockedDest.getAccountNumber(),
                    NotificationType.TRANSFER_SENT
            );
        }

        return receipt;
    }

    /**
     * Funds an account from the central simulation reserve (Admin only).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferReceiptResponse fundAccountSimulated(SimulatedFundingRequest request, UUID adminUserId) {
        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + adminUserId));

        BigDecimal scaledAmount = request.amount().setScale(2, RoundingMode.HALF_EVEN);
        if (scaledAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Funding amount must be strictly greater than zero");
        }

        BankAccount reserve = bankAccountRepository.findByAccountNumber(SYSTEM_RESERVE_ACCOUNT)
                .orElseThrow(() -> new ResourceNotFoundException("System reserve funding account not found"));

        BankAccount destination = bankAccountRepository.findByAccountNumber(request.destinationAccountNumber().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Destination account not found: " + request.destinationAccountNumber()));

        validateAccountStatusForTransfer(destination, "Destination");

        // Deterministic locking between reserve and destination
        UUID firstId = reserve.getId().compareTo(destination.getId()) < 0 ? reserve.getId() : destination.getId();
        UUID secondId = reserve.getId().compareTo(destination.getId()) < 0 ? destination.getId() : reserve.getId();

        BankAccount lockedFirst = bankAccountRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserve lock acquisition failed: " + firstId));
        BankAccount lockedSecond = bankAccountRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination lock acquisition failed: " + secondId));

        BankAccount lockedReserve = reserve.getId().equals(firstId) ? lockedFirst : lockedSecond;
        BankAccount lockedDest = destination.getId().equals(firstId) ? lockedFirst : lockedSecond;

        if (lockedReserve.getBalance().compareTo(scaledAmount) < 0) {
            throw new InsufficientFundsException("Central simulation reserve has insufficient liquidity");
        }

        String txRef = "TX-SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Transaction tx = new Transaction(
                txRef,
                lockedReserve,
                lockedDest,
                scaledAmount,
                lockedReserve.getCurrency(),
                TransactionType.SIMULATED_FUNDING,
                null,
                request.description() != null ? request.description() : "Simulated opening balance funding",
                adminUser
        );
        tx = transactionRepository.save(tx);

        ledgerService.postDoubleEntry(tx, lockedReserve, lockedDest, scaledAmount, tx.getDescription());
        tx.markCompleted();
        tx = transactionRepository.save(tx);

        auditService.recordEvent(
                AuditEventType.SIMULATED_FUNDING_POSTED,
                adminUser.getEmail(),
                "127.0.0.1",
                "FinCore-Backend",
                "SUCCESS",
                "Simulated funding of " + scaledAmount + " " + lockedReserve.getCurrency() + " to account " +
                        lockedDest.getAccountNumber() + " (Ref: " + txRef + ")"
        );

        return new TransferReceiptResponse(
                tx.getTransactionReference(),
                lockedReserve.getAccountNumber(),
                lockedDest.getAccountNumber(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getType().name(),
                tx.getStatus().name(),
                tx.getCompletedAt(),
                tx.getDescription(),
                null
        );
    }

    /**
     * Reverses a completed transfer by creating balanced compensating ledger entries (Admin only).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferReceiptResponse reverseTransaction(String transactionReference, ReversalRequest request, UUID adminUserId) {
        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + adminUserId));

        Transaction originalTx = transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionReference));

        if (originalTx.getStatus() != TransactionStatus.COMPLETED) {
            throw new InvalidTransferException("Cannot reverse transaction in status: " + originalTx.getStatus());
        }

        if (transactionRepository.existsByReversalOfTransaction_Id(originalTx.getId())) {
            throw new InvalidTransferException("Transaction " + transactionReference + " has already been reversed");
        }

        if (originalTx.getSourceAccount() == null || originalTx.getDestinationAccount() == null) {
            throw new InvalidTransferException("Transaction " + transactionReference + " does not have two distinct counterparty accounts for reversal");
        }

        // Reversal debits the original destination and credits the original source
        BankAccount origSource = originalTx.getSourceAccount();
        BankAccount origDest = originalTx.getDestinationAccount();

        UUID firstId = origSource.getId().compareTo(origDest.getId()) < 0 ? origSource.getId() : origDest.getId();
        UUID secondId = origSource.getId().compareTo(origDest.getId()) < 0 ? origDest.getId() : origSource.getId();

        BankAccount lockedFirst = bankAccountRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Reversal lock acquisition failed: " + firstId));
        BankAccount lockedSecond = bankAccountRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Reversal lock acquisition failed: " + secondId));

        BankAccount lockedOrigSource = origSource.getId().equals(firstId) ? lockedFirst : lockedSecond;
        BankAccount lockedOrigDest = origDest.getId().equals(firstId) ? lockedFirst : lockedSecond;

        // Verify that the account being debited has sufficient balance to be debited
        if (lockedOrigDest.getBalance().compareTo(originalTx.getAmount()) < 0) {
            throw new InsufficientFundsException("Account " + lockedOrigDest.getAccountNumber() + " has insufficient funds to execute reversal");
        }

        String revRef = "TX-REV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Transaction revTx = new Transaction(
                revRef,
                lockedOrigDest,
                lockedOrigSource,
                originalTx.getAmount(),
                originalTx.getCurrency(),
                TransactionType.REVERSAL,
                null,
                "Reversal of " + originalTx.getTransactionReference() + ": " + request.reason(),
                adminUser
        );
        revTx.setReversalOfTransaction(originalTx);
        revTx = transactionRepository.save(revTx);

        // Debit original destination, credit original source
        ledgerService.postDoubleEntry(revTx, lockedOrigDest, lockedOrigSource, originalTx.getAmount(), revTx.getDescription());

        originalTx.markReversed();
        transactionRepository.save(originalTx);

        revTx.markCompleted();
        revTx = transactionRepository.save(revTx);

        auditService.recordEvent(
                AuditEventType.TRANSACTION_REVERSED,
                adminUser.getEmail(),
                "127.0.0.1",
                "FinCore-Backend",
                "SUCCESS",
                "Reversed transaction " + originalTx.getTransactionReference() + " with reversal ref " + revRef +
                        ". Reason: " + request.reason()
        );

        return new TransferReceiptResponse(
                revTx.getTransactionReference(),
                lockedOrigDest.getAccountNumber(),
                lockedOrigSource.getAccountNumber(),
                revTx.getAmount(),
                revTx.getCurrency(),
                revTx.getType().name(),
                revTx.getStatus().name(),
                revTx.getCompletedAt(),
                revTx.getDescription(),
                null
        );
    }

    private void validateAccountStatusForTransfer(BankAccount account, String role) {
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new AccountFrozenException(role + " account " + account.getAccountNumber() + " is frozen");
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountClosedException(role + " account " + account.getAccountNumber() + " is closed");
        }
    }

    private String computeFingerprint(TransferRequest request) {
        String payload = request.sourceAccountNumber().trim() + "|" +
                request.destinationAccountNumber().trim() + "|" +
                request.amount().setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "|" +
                (request.currency() != null ? request.currency().toUpperCase() : "INR");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
