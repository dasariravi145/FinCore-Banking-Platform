package com.fincore.platform.loan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Single scheduled installment in the loan repayment schedule")
public record RepaymentScheduleResponse(
        UUID id,
        Integer installmentNumber,
        LocalDate dueDate,
        BigDecimal emiAmount,
        BigDecimal principalComponent,
        BigDecimal interestComponent,
        BigDecimal outstandingBalanceAfter,
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        String status,
        Instant paidAt
) {}

