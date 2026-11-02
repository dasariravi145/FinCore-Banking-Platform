package com.fincore.platform.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fincore.platform.account.entity.AccountStatus;
import com.fincore.platform.account.entity.AccountType;
import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.account.repository.BankAccountRepository;
import com.fincore.platform.common.audit.AuditEventType;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.*;
import com.fincore.platform.customer.entity.Customer;
import com.fincore.platform.ledger.entity.Transaction;
import com.fincore.platform.ledger.entity.TransactionStatus;
import com.fincore.platform.ledger.entity.TransactionType;
import com.fincore.platform.ledger.repository.TransactionRepository;
import com.fincore.platform.ledger.service.LedgerService;
import com.fincore.platform.transfer.dto.ReversalRequest;
import com.fincore.platform.transfer.dto.SimulatedFundingRequest;
import com.fincore.platform.transfer.dto.TransferReceiptResponse;
import com.fincore.platform.transfer.dto.TransferRequest;
import com.fincore.platform.transfer.entity.IdempotencyRecord;
import com.fincore.platform.transfer.entity.IdempotencyStatus;
import com.fincore.platform.transfer.repository.IdempotencyRecordRepository;
import com.fincore.platform.transfer.service.TransferService;
import com.fincore.platform.user.entity.Role;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.UserRepository;
import com.fincore.platform.fraud.service.FraudDetectionService;
import com.fincore.platform.notification.service.NotificationService;
import com.fincore.platform.notification.service.OutboxEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private SecurityAuditService auditService;

    @Mock
    private FraudDetectionService fraudDetectionService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private OutboxEventService outboxEventService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private TransferService transferService;

    private User senderUser;
    private User recipientUser;
    private User adminUser;
    private BankAccount sourceAccount;
    private BankAccount destinationAccount;
    private BankAccount reserveAccount;

    @BeforeEach
    void setUp() {
        senderUser = new User("sender@fincore.local", "hashed", "Aarav", "Sharma", "+919876543210");
        senderUser.setId(UUID.randomUUID());
        senderUser.setRoles(Set.of(new Role(RoleName.ROLE_CUSTOMER, "Customer")));

        recipientUser = new User("receiver@fincore.local", "hashed", "Priya", "Patel", "+919876543211");
        recipientUser.setId(UUID.randomUUID());
        recipientUser.setRoles(Set.of(new Role(RoleName.ROLE_CUSTOMER, "Customer")));

        adminUser = new User("admin@fincore.local", "hashed", "Admin", "User", "+919876543212");
        adminUser.setId(UUID.randomUUID());
        adminUser.setRoles(Set.of(new Role(RoleName.ROLE_ADMIN, "Admin")));

        Customer senderCustomer = new Customer(senderUser, "CUST-SENDER", "Aarav", "Sharma", senderUser.getEmail(), "+919876543210");
        senderCustomer.setId(UUID.randomUUID());

        Customer recipientCustomer = new Customer(recipientUser, "CUST-RECEIVER", "Priya", "Patel", recipientUser.getEmail(), "+919876543211");
        recipientCustomer.setId(UUID.randomUUID());

        sourceAccount = new BankAccount(senderCustomer, "ACC-100000000001", AccountType.SAVINGS, "INR");
        sourceAccount.setId(UUID.randomUUID());
        sourceAccount.setBalance(BigDecimal.valueOf(10000.00));

        destinationAccount = new BankAccount(recipientCustomer, "ACC-100000000002", AccountType.CURRENT, "INR");
        destinationAccount.setId(UUID.randomUUID());
        destinationAccount.setBalance(BigDecimal.valueOf(5000.00));

        reserveAccount = new BankAccount(null, "ACC-SYSTEM-RESERVE-001", AccountType.SYSTEM_RESERVE, "INR");
        reserveAccount.setId(UUID.randomUUID());
        reserveAccount.setBalance(BigDecimal.valueOf(100000000.00));
    }

    @Test
    @DisplayName("Execute transfer successfully with balanced double-entry and idempotency")
    void testTransferFunds_Success() {
        TransferRequest request = new TransferRequest(
                "ACC-100000000001", "ACC-100000000002",
                BigDecimal.valueOf(2500.00), "INR", "Rent payment"
        );
        String idemKey = UUID.randomUUID().toString();

        when(userRepository.findById(senderUser.getId())).thenReturn(Optional.of(senderUser));
        when(idempotencyRecordRepository.findByIdempotencyKey(idemKey)).thenReturn(Optional.empty());
        when(bankAccountRepository.findByAccountNumber("ACC-100000000001")).thenReturn(Optional.of(sourceAccount));
        when(bankAccountRepository.findByAccountNumber("ACC-100000000002")).thenReturn(Optional.of(destinationAccount));

        when(bankAccountRepository.findByIdWithLock(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(bankAccountRepository.findByIdWithLock(destinationAccount.getId())).thenReturn(Optional.of(destinationAccount));

        when(idempotencyRecordRepository.save(any(IdempotencyRecord.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction tx = i.getArgument(0);
            if (tx.getId() == null) tx.setId(UUID.randomUUID());
            return tx;
        });

        TransferReceiptResponse receipt = transferService.transferFunds(request, idemKey, senderUser.getId());

        assertThat(receipt).isNotNull();
        assertThat(receipt.transactionReference()).startsWith("TX-");
        assertThat(receipt.sourceAccountNumber()).isEqualTo("ACC-100000000001");
        assertThat(receipt.destinationAccountNumber()).isEqualTo("ACC-100000000002");
        assertThat(receipt.amount()).isEqualByComparingTo(BigDecimal.valueOf(2500.00));
        assertThat(receipt.status()).isEqualTo("COMPLETED");

        verify(ledgerService, times(1)).postDoubleEntry(
                any(Transaction.class), eq(sourceAccount), eq(destinationAccount),
                eq(BigDecimal.valueOf(2500.00).setScale(2)), eq("Rent payment")
        );
        verify(auditService, times(1)).recordEvent(
                eq(AuditEventType.TRANSFER_COMPLETED),
                eq(senderUser.getEmail()),
                any(), any(), eq("SUCCESS"), any()
        );
    }

    @Test
    @DisplayName("Self-transfer to same account rejected")
    void testTransferFunds_SelfTransferRejected() {
        TransferRequest request = new TransferRequest(
                "ACC-100000000001", "ACC-100000000001",
                BigDecimal.valueOf(500.00), "INR", "Self"
        );

        when(userRepository.findById(senderUser.getId())).thenReturn(Optional.of(senderUser));
        when(idempotencyRecordRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transferFunds(request, "idem-1", senderUser.getId()))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessageContaining("Cannot transfer funds to the same account");
    }

    @Test
    @DisplayName("Transfer rejected when user does not own source account")
    void testTransferFunds_UnauthorizedOwnershipRejected() {
        TransferRequest request = new TransferRequest(
                "ACC-100000000001", "ACC-100000000002",
                BigDecimal.valueOf(500.00), "INR", "Unauthorized"
        );

        when(userRepository.findById(recipientUser.getId())).thenReturn(Optional.of(recipientUser));
        when(idempotencyRecordRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(bankAccountRepository.findByAccountNumber("ACC-100000000001")).thenReturn(Optional.of(sourceAccount));
        when(bankAccountRepository.findByAccountNumber("ACC-100000000002")).thenReturn(Optional.of(destinationAccount));

        assertThatThrownBy(() -> transferService.transferFunds(request, "idem-1", recipientUser.getId()))
                .isInstanceOf(UnauthorizedAccountAccessException.class)
                .hasMessageContaining("does not have authorization");
    }

    @Test
    @DisplayName("Transfer rejected when source account is frozen")
    void testTransferFunds_FrozenSourceRejected() {
        sourceAccount.setStatus(AccountStatus.FROZEN);

        TransferRequest request = new TransferRequest(
                "ACC-100000000001", "ACC-100000000002",
                BigDecimal.valueOf(500.00), "INR", "Frozen"
        );

        when(userRepository.findById(senderUser.getId())).thenReturn(Optional.of(senderUser));
        when(idempotencyRecordRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(bankAccountRepository.findByAccountNumber("ACC-100000000001")).thenReturn(Optional.of(sourceAccount));
        when(bankAccountRepository.findByAccountNumber("ACC-100000000002")).thenReturn(Optional.of(destinationAccount));

        assertThatThrownBy(() -> transferService.transferFunds(request, "idem-1", senderUser.getId()))
                .isInstanceOf(AccountFrozenException.class)
                .hasMessageContaining("Source account ACC-100000000001 is frozen");
    }

    @Test
    @DisplayName("Transfer rejected when destination account is closed")
    void testTransferFunds_ClosedDestinationRejected() {
        destinationAccount.setStatus(AccountStatus.CLOSED);

        TransferRequest request = new TransferRequest(
                "ACC-100000000001", "ACC-100000000002",
                BigDecimal.valueOf(500.00), "INR", "Closed Dest"
        );

        when(userRepository.findById(senderUser.getId())).thenReturn(Optional.of(senderUser));
        when(idempotencyRecordRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(bankAccountRepository.findByAccountNumber("ACC-100000000001")).thenReturn(Optional.of(sourceAccount));
        when(bankAccountRepository.findByAccountNumber("ACC-100000000002")).thenReturn(Optional.of(destinationAccount));

        assertThatThrownBy(() -> transferService.transferFunds(request, "idem-1", senderUser.getId()))
                .isInstanceOf(AccountClosedException.class)
                .hasMessageContaining("Destination account ACC-100000000002 is closed");
    }

    @Test
    @DisplayName("Same idempotency key with modified payload throws IdempotencyPayloadMismatchException")
    void testTransferFunds_IdempotencyPayloadMismatch() {
        TransferRequest originalRequest = new TransferRequest(
                "ACC-100000000001", "ACC-100000000002",
                BigDecimal.valueOf(100.00), "INR", "Original"
        );
        String idemKey = "idem-key-duplicate";

        IdempotencyRecord existingRecord = new IdempotencyRecord(
                idemKey, senderUser, "DIFFERENT_HASH_FINGERPRINT",
                IdempotencyStatus.COMPLETED, Instant.now().plusSeconds(3600)
        );

        when(userRepository.findById(senderUser.getId())).thenReturn(Optional.of(senderUser));
        when(idempotencyRecordRepository.findByIdempotencyKey(idemKey)).thenReturn(Optional.of(existingRecord));

        assertThatThrownBy(() -> transferService.transferFunds(originalRequest, idemKey, senderUser.getId()))
                .isInstanceOf(IdempotencyPayloadMismatchException.class)
                .hasMessageContaining("already been used with different request parameters");
    }

    @Test
    @DisplayName("Admin funds account from simulation reserve successfully")
    void testFundAccountSimulated_Success() {
        SimulatedFundingRequest request = new SimulatedFundingRequest(
                "ACC-100000000001", BigDecimal.valueOf(50000.00), "Simulated Seed Funding"
        );

        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(bankAccountRepository.findByAccountNumber("ACC-SYSTEM-RESERVE-001")).thenReturn(Optional.of(reserveAccount));
        when(bankAccountRepository.findByAccountNumber("ACC-100000000001")).thenReturn(Optional.of(sourceAccount));

        when(bankAccountRepository.findByIdWithLock(reserveAccount.getId())).thenReturn(Optional.of(reserveAccount));
        when(bankAccountRepository.findByIdWithLock(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction tx = i.getArgument(0);
            if (tx.getId() == null) tx.setId(UUID.randomUUID());
            return tx;
        });

        TransferReceiptResponse receipt = transferService.fundAccountSimulated(request, adminUser.getId());

        assertThat(receipt).isNotNull();
        assertThat(receipt.type()).isEqualTo("SIMULATED_FUNDING");
        assertThat(receipt.amount()).isEqualByComparingTo(BigDecimal.valueOf(50000.00));
        assertThat(receipt.destinationAccountNumber()).isEqualTo("ACC-100000000001");

        verify(ledgerService, times(1)).postDoubleEntry(
                any(Transaction.class), eq(reserveAccount), eq(sourceAccount),
                eq(BigDecimal.valueOf(50000.00).setScale(2)), eq("Simulated Seed Funding")
        );
    }

    @Test
    @DisplayName("Admin reverses a completed transaction successfully")
    void testReverseTransaction_Success() {
        Transaction originalTx = new Transaction(
                "TX-ORIGINAL-001", sourceAccount, destinationAccount,
                BigDecimal.valueOf(1000.00), "INR", TransactionType.INTERNAL_TRANSFER,
                "idem-orig", "Original transfer", senderUser
        );
        originalTx.setId(UUID.randomUUID());
        originalTx.markCompleted();

        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(transactionRepository.findByTransactionReference("TX-ORIGINAL-001")).thenReturn(Optional.of(originalTx));
        when(transactionRepository.existsByReversalOfTransaction_Id(originalTx.getId())).thenReturn(false);

        when(bankAccountRepository.findByIdWithLock(sourceAccount.getId())).thenReturn(Optional.of(sourceAccount));
        when(bankAccountRepository.findByIdWithLock(destinationAccount.getId())).thenReturn(Optional.of(destinationAccount));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction tx = i.getArgument(0);
            if (tx.getId() == null) tx.setId(UUID.randomUUID());
            return tx;
        });

        ReversalRequest request = new ReversalRequest("Disputed chargeback");
        TransferReceiptResponse receipt = transferService.reverseTransaction("TX-ORIGINAL-001", request, adminUser.getId());

        assertThat(receipt).isNotNull();
        assertThat(receipt.type()).isEqualTo("REVERSAL");
        assertThat(receipt.amount()).isEqualByComparingTo(BigDecimal.valueOf(1000.00));
        assertThat(originalTx.getStatus()).isEqualTo(TransactionStatus.REVERSED);

        // Compensating ledger entry: debits original destination, credits original source
        verify(ledgerService, times(1)).postDoubleEntry(
                any(Transaction.class), eq(destinationAccount), eq(sourceAccount),
                argThat(a -> a != null && a.compareTo(BigDecimal.valueOf(1000.00)) == 0), anyString()
        );
    }
}
