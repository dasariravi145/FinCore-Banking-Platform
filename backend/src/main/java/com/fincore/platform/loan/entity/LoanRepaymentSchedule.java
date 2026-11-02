package com.fincore.platform.loan.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "loan_repayment_schedules", uniqueConstraints = {
        @UniqueConstraint(name = "uq_loan_installment", columnNames = {"loan_id", "installment_number"})
})
public class LoanRepaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "emi_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal emiAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "principal_component", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalComponent = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "interest_component", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestComponent = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "outstanding_balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal outstandingBalanceAfter = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "principal_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "interest_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal interestPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScheduleStatus status = ScheduleStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public LoanRepaymentSchedule() {}

    public LoanRepaymentSchedule(Integer installmentNumber, LocalDate dueDate, BigDecimal emiAmount,
                                 BigDecimal principalComponent, BigDecimal interestComponent,
                                 BigDecimal outstandingBalanceAfter) {
        this.installmentNumber = installmentNumber;
        this.dueDate = dueDate;
        this.emiAmount = emiAmount.setScale(2, RoundingMode.HALF_EVEN);
        this.principalComponent = principalComponent.setScale(2, RoundingMode.HALF_EVEN);
        this.interestComponent = interestComponent.setScale(2, RoundingMode.HALF_EVEN);
        this.outstandingBalanceAfter = outstandingBalanceAfter.setScale(2, RoundingMode.HALF_EVEN);
        this.principalPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        this.interestPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        this.status = ScheduleStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isPaid() {
        return status == ScheduleStatus.PAID;
    }

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Loan getLoan() { return loan; }
    public void setLoan(Loan loan) { this.loan = loan; }
    public Integer getInstallmentNumber() { return installmentNumber; }
    public void setInstallmentNumber(Integer installmentNumber) { this.installmentNumber = installmentNumber; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getEmiAmount() { return emiAmount; }
    public void setEmiAmount(BigDecimal emiAmount) { this.emiAmount = emiAmount; }
    public BigDecimal getPrincipalComponent() { return principalComponent; }
    public void setPrincipalComponent(BigDecimal principalComponent) { this.principalComponent = principalComponent; }
    public BigDecimal getInterestComponent() { return interestComponent; }
    public void setInterestComponent(BigDecimal interestComponent) { this.interestComponent = interestComponent; }
    public BigDecimal getOutstandingBalanceAfter() { return outstandingBalanceAfter; }
    public void setOutstandingBalanceAfter(BigDecimal outstandingBalanceAfter) { this.outstandingBalanceAfter = outstandingBalanceAfter; }
    public BigDecimal getPrincipalPaid() { return principalPaid; }
    public void setPrincipalPaid(BigDecimal principalPaid) { this.principalPaid = principalPaid; }
    public BigDecimal getInterestPaid() { return interestPaid; }
    public void setInterestPaid(BigDecimal interestPaid) { this.interestPaid = interestPaid; }
    public ScheduleStatus getStatus() { return status; }
    public void setStatus(ScheduleStatus status) { this.status = status; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LoanRepaymentSchedule that = (LoanRepaymentSchedule) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

