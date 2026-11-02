package com.fincore.platform.loan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Detailed loan profile, status, and repayment schedule")
public record LoanResponse(
        UUID id,
        String loanNumber,
        String customerNumber,
        String customerName,
        String disbursementAccountNumber,
        String loanType,
        BigDecimal principalAmount,
        BigDecimal interestRate,
        Integer termMonths,
        BigDecimal monthlyEmi,
        BigDecimal totalInterestCharged,
        BigDecimal outstandingPrincipal,
        BigDecimal totalPrincipalPaid,
        BigDecimal totalInterestPaid,
        String status,
        String rejectionReason,
        Instant appliedAt,
        Instant approvedAt,
        Instant disbursedAt,
        Instant closedAt,
        List<RepaymentScheduleResponse> schedules
) {}

