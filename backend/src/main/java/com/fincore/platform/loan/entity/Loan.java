package com.fincore.platform.loan.entity;

import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.customer.entity.Customer;
import com.fincore.platform.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "loan_number", nullable = false, unique = true, length = 32)
    private String loanNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disbursement_account_id", nullable = false)
    private BankAccount disbursementAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 30)
    private LoanType loanType;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "interest_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal interestRate = BigDecimal.ZERO;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "monthly_emi", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyEmi = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "total_interest_charged", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalInterestCharged = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "outstanding_principal", nullable = false, precision = 19, scale = 2)
    private BigDecimal outstandingPrincipal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "total_principal_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPrincipalPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "total_interest_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalInterestPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LoanStatus status = LoanStatus.APPLIED;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt = Instant.now();

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version = 0L;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("installmentNumber ASC")
    private List<LoanRepaymentSchedule> schedules = new ArrayList<>();

    public Loan() {}

    public Loan(Customer customer, BankAccount disbursementAccount, LoanType loanType,
                BigDecimal principalAmount, BigDecimal interestRate, Integer termMonths,
                BigDecimal monthlyEmi, BigDecimal totalInterestCharged) {
        this.customer = customer;
        this.disbursementAccount = disbursementAccount;
        this.loanType = loanType;
        this.principalAmount = principalAmount.setScale(2, RoundingMode.HALF_EVEN);
        this.interestRate = interestRate;
        this.termMonths = termMonths;
        this.monthlyEmi = monthlyEmi.setScale(2, RoundingMode.HALF_EVEN);
        this.totalInterestCharged = totalInterestCharged.setScale(2, RoundingMode.HALF_EVEN);
        this.outstandingPrincipal = principalAmount.setScale(2, RoundingMode.HALF_EVEN);
        this.totalPrincipalPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        this.totalInterestPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        this.status = LoanStatus.APPLIED;
        this.appliedAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void addSchedule(LoanRepaymentSchedule schedule) {
        schedules.add(schedule);
        schedule.setLoan(this);
    }

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getLoanNumber() { return loanNumber; }
    public void setLoanNumber(String loanNumber) { this.loanNumber = loanNumber; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public BankAccount getDisbursementAccount() { return disbursementAccount; }
    public void setDisbursementAccount(BankAccount disbursementAccount) { this.disbursementAccount = disbursementAccount; }
    public LoanType getLoanType() { return loanType; }
    public void setLoanType(LoanType loanType) { this.loanType = loanType; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public Integer getTermMonths() { return termMonths; }
    public void setTermMonths(Integer termMonths) { this.termMonths = termMonths; }
    public BigDecimal getMonthlyEmi() { return monthlyEmi; }
    public void setMonthlyEmi(BigDecimal monthlyEmi) { this.monthlyEmi = monthlyEmi; }
    public BigDecimal getTotalInterestCharged() { return totalInterestCharged; }
    public void setTotalInterestCharged(BigDecimal totalInterestCharged) { this.totalInterestCharged = totalInterestCharged; }
    public BigDecimal getOutstandingPrincipal() { return outstandingPrincipal; }
    public void setOutstandingPrincipal(BigDecimal outstandingPrincipal) { this.outstandingPrincipal = outstandingPrincipal; }
    public BigDecimal getTotalPrincipalPaid() { return totalPrincipalPaid; }
    public void setTotalPrincipalPaid(BigDecimal totalPrincipalPaid) { this.totalPrincipalPaid = totalPrincipalPaid; }
    public BigDecimal getTotalInterestPaid() { return totalInterestPaid; }
    public void setTotalInterestPaid(BigDecimal totalInterestPaid) { this.totalInterestPaid = totalInterestPaid; }
    public LoanStatus getStatus() { return status; }
    public void setStatus(LoanStatus status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public User getApprovedBy() { return approvedBy; }
    public void setApprovedBy(User approvedBy) { this.approvedBy = approvedBy; }
    public Instant getDisbursedAt() { return disbursedAt; }
    public void setDisbursedAt(Instant disbursedAt) { this.disbursedAt = disbursedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public List<LoanRepaymentSchedule> getSchedules() { return schedules; }
    public void setSchedules(List<LoanRepaymentSchedule> schedules) { this.schedules = schedules; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Loan loan = (Loan) o;
        return Objects.equals(loanNumber, loan.loanNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loanNumber);
    }
}

