package com.fincore.platform.ledger.entity;

import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 64)
    private String transactionReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private BankAccount sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id")
    private BankAccount destinationAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionStatus status;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(length = 255)
    private String description;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_transaction_id")
    private Transaction reversalOfTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by_user_id")
    private User initiatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    public Transaction() {}

    public Transaction(String transactionReference, BankAccount sourceAccount, BankAccount destinationAccount,
                       BigDecimal amount, String currency, TransactionType type, String idempotencyKey,
                       String description, User initiatedBy) {
        this.transactionReference = transactionReference;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount.setScale(2, RoundingMode.HALF_EVEN);
        this.currency = currency != null ? currency : "INR";
        this.type = type;
        this.status = TransactionStatus.PENDING;
        this.idempotencyKey = idempotencyKey;
        this.description = description;
        this.initiatedBy = initiatedBy;
        this.createdAt = Instant.now();
    }

    public void markCompleted() {
        this.status = TransactionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markFailed() {
        this.status = TransactionStatus.FAILED;
        this.completedAt = Instant.now();
    }

    public void markReversed() {
        this.status = TransactionStatus.REVERSED;
    }

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }
    public BankAccount getSourceAccount() { return sourceAccount; }
    public void setSourceAccount(BankAccount sourceAccount) { this.sourceAccount = sourceAccount; }
    public BankAccount getDestinationAccount() { return destinationAccount; }
    public void setDestinationAccount(BankAccount destinationAccount) { this.destinationAccount = destinationAccount; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) {
        this.amount = amount != null ? amount.setScale(2, RoundingMode.HALF_EVEN) : null;
    }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Transaction getReversalOfTransaction() { return reversalOfTransaction; }
    public void setReversalOfTransaction(Transaction reversalOfTransaction) { this.reversalOfTransaction = reversalOfTransaction; }
    public User getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(User initiatedBy) { this.initiatedBy = initiatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return Objects.equals(transactionReference, that.transactionReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionReference);
    }
}

