package com.fincore.platform.fraud.service;

import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.common.audit.AuditEventType;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.FraudBlockedException;
import com.fincore.platform.common.error.ResourceNotFoundException;
import com.fincore.platform.fraud.dto.FraudAssessmentResponse;
import com.fincore.platform.fraud.dto.FraudReviewRequest;
import com.fincore.platform.fraud.entity.FraudAssessment;
import com.fincore.platform.fraud.entity.FraudAssessmentStatus;
import com.fincore.platform.fraud.entity.FraudDecision;
import com.fincore.platform.fraud.entity.RiskLevel;
import com.fincore.platform.fraud.repository.FraudAssessmentRepository;
import com.fincore.platform.fraud.rules.FraudRule;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    private final List<FraudRule> rules;
    private final FraudAssessmentRepository fraudAssessmentRepository;
    private final UserRepository userRepository;
    private final SecurityAuditService auditService;

    public FraudDetectionService(List<FraudRule> rules,
                                 FraudAssessmentRepository fraudAssessmentRepository,
                                 UserRepository userRepository,
                                 SecurityAuditService auditService) {
        this.rules = rules;
        this.fraudAssessmentRepository = fraudAssessmentRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Evaluates fraud risk on an impending transfer.
     * Throws FraudBlockedException if the evaluation results in BLOCK.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public FraudAssessment evaluateAndEnforce(BankAccount sourceAccount, BankAccount destinationAccount,
                                              BigDecimal amount, String txRef) {
        int totalScore = 0;
        List<String> triggeredReasons = new ArrayList<>();

        for (FraudRule rule : rules) {
            int score = rule.evaluate(sourceAccount, destinationAccount, amount);
            if (score > 0) {
                totalScore += score;
                triggeredReasons.add(rule.getRuleName() + ": " + rule.getExplanation());
            }
        }

        int normalizedScore = Math.min(totalScore, 100);

        RiskLevel riskLevel;
        FraudDecision decision;

        if (normalizedScore >= 70) {
            riskLevel = normalizedScore >= 90 ? RiskLevel.CRITICAL : RiskLevel.HIGH;
            decision = FraudDecision.BLOCK;
        } else if (normalizedScore >= 40) {
            riskLevel = RiskLevel.MEDIUM;
            decision = FraudDecision.REVIEW;
        } else {
            riskLevel = RiskLevel.LOW;
            decision = FraudDecision.ALLOW;
        }

        String triggeredSummary = String.join("; ", triggeredReasons);

        FraudAssessment assessment = new FraudAssessment(
                txRef,
                sourceAccount,
                destinationAccount,
                amount,
                normalizedScore,
                riskLevel,
                decision,
                triggeredSummary.isEmpty() ? "No rules triggered" : triggeredSummary
        );

        assessment = fraudAssessmentRepository.save(assessment);

        log.info("Fraud assessment for TX [{}] from [{}] to [{}]: score={}, level={}, decision={}",
                txRef, sourceAccount.getAccountNumber(), destinationAccount.getAccountNumber(),
                normalizedScore, riskLevel, decision);

        if (decision == FraudDecision.BLOCK) {
            auditService.recordEvent(
                    AuditEventType.FRAUD_FLAGGED,
                    sourceAccount.getCustomer() != null ? sourceAccount.getCustomer().getEmail() : "SYSTEM",
                    "127.0.0.1",
                    "FinCore-FraudEngine",
                    "BLOCKED",
                    "Transfer blocked by fraud detection: " + triggeredSummary
            );
            throw new FraudBlockedException("Transfer blocked by fraud detection rules: " + triggeredSummary);
        }

        if (decision == FraudDecision.REVIEW) {
            auditService.recordEvent(
                    AuditEventType.FRAUD_FLAGGED,
                    sourceAccount.getCustomer() != null ? sourceAccount.getCustomer().getEmail() : "SYSTEM",
                    "127.0.0.1",
                    "FinCore-FraudEngine",
                    "FLAGGED_FOR_REVIEW",
                    "Transfer flagged for manual review: " + triggeredSummary
            );
        }

        return assessment;
    }

    @Transactional(readOnly = true)
    public Page<FraudAssessmentResponse> getAssessments(Pageable pageable, FraudDecision decision, FraudAssessmentStatus status) {
        Page<FraudAssessment> page;
        if (decision != null) {
            page = fraudAssessmentRepository.findByDecisionOrderByCreatedAtDesc(decision, pageable);
        } else if (status != null) {
            page = fraudAssessmentRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            page = fraudAssessmentRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional
    public FraudAssessmentResponse reviewAssessment(UUID assessmentId, FraudReviewRequest request, UUID adminUserId) {
        FraudAssessment assessment = fraudAssessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Fraud assessment not found: " + assessmentId));

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + adminUserId));

        assessment.setStatus(request.status());
        assessment.setReviewedBy(adminUser);
        assessment.setReviewNotes(request.reviewNotes());
        assessment.setReviewedAt(Instant.now());

        FraudAssessment saved = fraudAssessmentRepository.save(assessment);

        auditService.recordEvent(
                AuditEventType.FRAUD_REVIEWED,
                adminUser.getEmail(),
                "127.0.0.1",
                "FinCore-FraudEngine",
                "SUCCESS",
                "Fraud assessment " + assessmentId + " reviewed: " + request.status() + ". Notes: " + request.reviewNotes()
        );

        return toResponse(saved);
    }

    private FraudAssessmentResponse toResponse(FraudAssessment fa) {
        return new FraudAssessmentResponse(
                fa.getId(),
                fa.getTransactionReference(),
                fa.getSourceAccount() != null ? fa.getSourceAccount().getAccountNumber() : null,
                fa.getDestinationAccount() != null ? fa.getDestinationAccount().getAccountNumber() : null,
                fa.getAmount(),
                fa.getRiskScore(),
                fa.getRiskLevel().name(),
                fa.getDecision().name(),
                fa.getTriggeredRules(),
                fa.getStatus().name(),
                fa.getReviewedBy() != null ? fa.getReviewedBy().getEmail() : null,
                fa.getReviewNotes(),
                fa.getCreatedAt(),
                fa.getReviewedAt()
        );
    }
}

