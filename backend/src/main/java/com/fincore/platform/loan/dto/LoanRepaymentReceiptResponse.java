package com.fincore.platform.loan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Receipt of a processed loan repayment")
public record LoanRepaymentReceiptResponse(
        String paymentReference,
        String loanNumber,
        String sourceAccountNumber,
        BigDecimal totalPaid,
        BigDecimal principalPaid,
        BigDecimal interestPaid,
        BigDecimal remainingOutstandingPrincipal,
        String loanStatus,
        Instant paidAt,
        String message
) {}

