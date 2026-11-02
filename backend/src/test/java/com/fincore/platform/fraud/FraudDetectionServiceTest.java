package com.fincore.platform.fraud;

import com.fincore.platform.account.entity.AccountType;
import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.common.audit.AuditEventType;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.FraudBlockedException;
import com.fincore.platform.customer.entity.Customer;
import com.fincore.platform.fraud.dto.FraudAssessmentResponse;
import com.fincore.platform.fraud.dto.FraudReviewRequest;
import com.fincore.platform.fraud.entity.*;
import com.fincore.platform.fraud.repository.FraudAssessmentRepository;
import com.fincore.platform.fraud.rules.ExcessiveBalanceDepletionRule;
import com.fincore.platform.fraud.rules.FraudRule;
import com.fincore.platform.fraud.rules.HighValueTransferRule;
import com.fincore.platform.fraud.rules.RapidVelocityRule;
import com.fincore.platform.fraud.service.FraudDetectionService;
import com.fincore.platform.ledger.repository.TransactionRepository;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock
    private FraudAssessmentRepository fraudAssessmentRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityAuditService auditService;

    private FraudDetectionService fraudDetectionService;
    private BankAccount sourceAccount;
    private BankAccount destinationAccount;
    private User adminUser;

    @BeforeEach
    void setUp() {
        HighValueTransferRule highValueRule = new HighValueTransferRule();
        RapidVelocityRule rapidVelocityRule = new RapidVelocityRule(transactionRepository);
        ExcessiveBalanceDepletionRule depletionRule = new ExcessiveBalanceDepletionRule();

        List<FraudRule> rules = List.of(highValueRule, rapidVelocityRule, depletionRule);
        fraudDetectionService = new FraudDetectionService(rules, fraudAssessmentRepository, userRepository, auditService);

        Customer customer = new Customer(null, "CUST-001", "Fraud", "Test", "fraud@test.local", "+919876543000");
        sourceAccount = new BankAccount(customer, "ACC-SRC-001", AccountType.SAVINGS, "INR");
        sourceAccount.setId(UUID.randomUUID());
        sourceAccount.setBalance(BigDecimal.valueOf(1000000.00)); // 10 Lakhs

        destinationAccount = new BankAccount(customer, "ACC-DST-001", AccountType.CURRENT, "INR");
        destinationAccount.setId(UUID.randomUUID());
        destinationAccount.setBalance(BigDecimal.valueOf(50000.00));

        adminUser = new User("admin@test.local", "hashed", "Admin", "User", "+919876543999");
        adminUser.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Normal small transfer produces ALLOW decision with LOW risk level")
    void testSmallTransfer_Allowed() {
        when(transactionRepository.countRecentBySourceAccount(eq(sourceAccount.getId()), any(Instant.class))).thenReturn(0L);
        when(fraudAssessmentRepository.save(any(FraudAssessment.class))).thenAnswer(i -> i.getArgument(0));

        FraudAssessment assessment = fraudDetectionService.evaluateAndEnforce(
                sourceAccount, destinationAccount, BigDecimal.valueOf(5000.00), "TX-TEST-001"
        );

        assertThat(assessment.getDecision()).isEqualTo(FraudDecision.ALLOW);
        assertThat(assessment.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(assessment.getRiskScore()).isEqualTo(0);
    }

    @Test
    @DisplayName("High value transfer (> ₹500,000) triggers high value rule yielding REVIEW or BLOCK")
    void testHighValueTransfer_TriggersRule() {
        when(transactionRepository.countRecentBySourceAccount(eq(sourceAccount.getId()), any(Instant.class))).thenReturn(0L);
        when(fraudAssessmentRepository.save(any(FraudAssessment.class))).thenAnswer(i -> i.getArgument(0));

        // Transfer 600,000 on balance of 1,000,000 (ratio 0.60, not excessive depletion, score 50 -> REVIEW)
        FraudAssessment assessment = fraudDetectionService.evaluateAndEnforce(
                sourceAccount, destinationAccount, BigDecimal.valueOf(600000.00), "TX-HIGH-001"
        );

        assertThat(assessment.getDecision()).isEqualTo(FraudDecision.REVIEW);
        assertThat(assessment.getRiskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(assessment.getRiskScore()).isEqualTo(50);
        assertThat(assessment.getTriggeredRules()).contains("HIGH_VALUE_TRANSFER");
    }

    @Test
    @DisplayName("High value combined with excessive balance depletion reaches >= 70 score and BLOCKS transfer")
    void testCombinedHighRisk_BlocksTransfer() {
        when(transactionRepository.countRecentBySourceAccount(eq(sourceAccount.getId()), any(Instant.class))).thenReturn(0L);
        when(fraudAssessmentRepository.save(any(FraudAssessment.class))).thenAnswer(i -> i.getArgument(0));

        // Transfer 980,000 on balance of 1,000,000 (ratio 0.98 >= 0.95: +25; high value: +50; total: 75 -> BLOCK)
        assertThatThrownBy(() -> fraudDetectionService.evaluateAndEnforce(
                sourceAccount, destinationAccount, BigDecimal.valueOf(980000.00), "TX-CRIT-001"
        ))
                .isInstanceOf(FraudBlockedException.class)
                .hasMessageContaining("Transfer blocked by fraud detection rules");

        verify(auditService).recordEvent(eq(AuditEventType.FRAUD_FLAGGED), anyString(), anyString(), anyString(), eq("BLOCKED"), anyString());
    }

    @Test
    @DisplayName("Rapid transfer velocity combined with high value BLOCKS transfer")
    void testRapidVelocity_BlocksTransfer() {
        // 4 recent transfers from source in last 5 minutes -> +40 points
        when(transactionRepository.countRecentBySourceAccount(eq(sourceAccount.getId()), any(Instant.class))).thenReturn(4L);
        when(fraudAssessmentRepository.save(any(FraudAssessment.class))).thenAnswer(i -> i.getArgument(0));

        // Transfer 550,000 -> +50 points. Total = 90 -> BLOCK
        assertThatThrownBy(() -> fraudDetectionService.evaluateAndEnforce(
                sourceAccount, destinationAccount, BigDecimal.valueOf(550000.00), "TX-VELOCITY-001"
        ))
                .isInstanceOf(FraudBlockedException.class)
                .hasMessageContaining("Transfer blocked by fraud detection rules");
    }

    @Test
    @DisplayName("Admin manual review updates fraud assessment status and records notes")
    void testReviewAssessment() {
        FraudAssessment assessment = new FraudAssessment("TX-001", sourceAccount, destinationAccount, BigDecimal.valueOf(600000.00), 50, RiskLevel.MEDIUM, FraudDecision.REVIEW, "HIGH_VALUE_TRANSFER");
        assessment.setId(UUID.randomUUID());

        when(fraudAssessmentRepository.findById(assessment.getId())).thenReturn(Optional.of(assessment));
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(fraudAssessmentRepository.save(any(FraudAssessment.class))).thenAnswer(i -> i.getArgument(0));

        FraudReviewRequest request = new FraudReviewRequest(FraudAssessmentStatus.APPROVED, "Verified customer identity via callback");
        FraudAssessmentResponse response = fraudDetectionService.reviewAssessment(assessment.getId(), request, adminUser.getId());

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.reviewNotes()).isEqualTo("Verified customer identity via callback");
        assertThat(response.reviewedByEmail()).isEqualTo(adminUser.getEmail());
        verify(auditService).recordEvent(eq(AuditEventType.FRAUD_REVIEWED), eq(adminUser.getEmail()), anyString(), anyString(), eq("SUCCESS"), anyString());
    }
}
