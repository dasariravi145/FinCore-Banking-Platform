package com.fincore.platform.fraud.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Fraud assessment evaluation report for a transaction")
public record FraudAssessmentResponse(
        UUID id,
        String transactionReference,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        Integer riskScore,
        String riskLevel,
        String decision,
        String triggeredRules,
        String status,
        String reviewedByEmail,
        String reviewNotes,
        Instant createdAt,
        Instant reviewedAt
) {}

