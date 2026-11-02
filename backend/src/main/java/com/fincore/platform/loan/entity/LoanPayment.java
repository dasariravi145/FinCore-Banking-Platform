package com.fincore.platform.loan.entity;

import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.ledger.entity.Transaction;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "loan_payments")
public class LoanPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private LoanRepaymentSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", nullable = false)
    private BankAccount sourceAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "principal_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "interest_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "payment_reference", nullable = false, unique = true, length = 64)
    private String paymentReference;

    @Column(name = "paid_at", nullable = false, updatable = false)
    private Instant paidAt = Instant.now();

    public LoanPayment() {}

    public LoanPayment(Loan loan, LoanRepaymentSchedule schedule, Transaction transaction,
                       BankAccount sourceAccount, BigDecimal amount, BigDecimal principalPaid,
                       BigDecimal interestPaid, String paymentReference) {
        this.loan = loan;
        this.schedule = schedule;
        this.transaction = transaction;
        this.sourceAccount = sourceAccount;
        this.amount = amount.setScale(2, RoundingMode.HALF_EVEN);
        this.principalPaid = principalPaid.setScale(2, RoundingMode.HALF_EVEN);
        this.interestPaid = interestPaid.setScale(2, RoundingMode.HALF_EVEN);
        this.paymentReference = paymentReference;
        this.paidAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Loan getLoan() { return loan; }
    public void setLoan(Loan loan) { this.loan = loan; }
    public LoanRepaymentSchedule getSchedule() { return schedule; }
    public void setSchedule(LoanRepaymentSchedule schedule) { this.schedule = schedule; }
    public Transaction getTransaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }
    public BankAccount getSourceAccount() { return sourceAccount; }
    public void setSourceAccount(BankAccount sourceAccount) { this.sourceAccount = sourceAccount; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getPrincipalPaid() { return principalPaid; }
    public void setPrincipalPaid(BigDecimal principalPaid) { this.principalPaid = principalPaid; }
    public BigDecimal getInterestPaid() { return interestPaid; }
    public void setInterestPaid(BigDecimal interestPaid) { this.interestPaid = interestPaid; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LoanPayment that = (LoanPayment) o;
        return Objects.equals(paymentReference, that.paymentReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentReference);
    }
}

