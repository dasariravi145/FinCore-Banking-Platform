package com.fincore.platform.loan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Customer request to make a loan repayment payment")
public record LoanRepaymentRequest(
        @Schema(description = "Repayment payment amount in INR", example = "15000.00")
        @NotNull(message = "Payment amount is required")
        @DecimalMin(value = "1.00", message = "Payment amount must be at least ₹1.00")
        BigDecimal amount,

        @Schema(description = "Source account number from which funds will be debited", example = "ACC-1234567890")
        @NotBlank(message = "Source account number is required")
        String sourceAccountNumber,

        @Schema(description = "Optional payment note", example = "Monthly EMI payment")
        String description
) {}

