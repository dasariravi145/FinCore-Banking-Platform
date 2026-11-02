package com.fincore.platform.fraud.entity;

import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "fraud_assessments")
public class FraudAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_reference", length = 64)
    private String transactionReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private BankAccount sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id")
    private BankAccount destinationAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FraudDecision decision;

    @Column(name = "triggered_rules", columnDefinition = "TEXT")
    private String triggeredRules;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FraudAssessmentStatus status = FraudAssessmentStatus.PENDING_REVIEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "review_notes", length = 500)
    private String reviewNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public FraudAssessment() {}

    public FraudAssessment(String transactionReference, BankAccount sourceAccount,
                           BankAccount destinationAccount, BigDecimal amount,
                           Integer riskScore, RiskLevel riskLevel, FraudDecision decision,
                           String triggeredRules) {
        this.transactionReference = transactionReference;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount != null ? amount.setScale(2, RoundingMode.HALF_EVEN) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.decision = decision;
        this.triggeredRules = triggeredRules;
        this.status = decision == FraudDecision.ALLOW ? FraudAssessmentStatus.APPROVED : FraudAssessmentStatus.PENDING_REVIEW;
        this.createdAt = Instant.now();
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
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public FraudDecision getDecision() { return decision; }
    public void setDecision(FraudDecision decision) { this.decision = decision; }
    public String getTriggeredRules() { return triggeredRules; }
    public void setTriggeredRules(String triggeredRules) { this.triggeredRules = triggeredRules; }
    public FraudAssessmentStatus getStatus() { return status; }
    public void setStatus(FraudAssessmentStatus status) { this.status = status; }
    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FraudAssessment that = (FraudAssessment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

