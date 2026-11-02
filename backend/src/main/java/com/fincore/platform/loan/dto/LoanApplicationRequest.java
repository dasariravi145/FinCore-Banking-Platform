package com.fincore.platform.loan.dto;

import com.fincore.platform.loan.entity.LoanType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Customer application request for a new loan")
public record LoanApplicationRequest(
        @Schema(description = "Loan product type", example = "PERSONAL")
        @NotNull(message = "Loan type is required")
        LoanType loanType,

        @Schema(description = "Requested principal amount in INR", example = "200000.00")
        @NotNull(message = "Principal amount is required")
        @DecimalMin(value = "1000.00", message = "Principal amount must be at least ₹1,000.00")
        @DecimalMax(value = "10000000.00", message = "Principal amount cannot exceed ₹10,000,000.00")
        BigDecimal principalAmount,

        @Schema(description = "Requested loan tenure in months", example = "12")
        @NotNull(message = "Tenure in months is required")
        @Min(value = 1, message = "Tenure must be at least 1 month")
        @Max(value = 360, message = "Tenure cannot exceed 360 months")
        Integer termMonths,

        @Schema(description = "Active customer bank account number for receiving disbursement", example = "ACC-1234567890")
        @NotBlank(message = "Disbursement account number is required")
        String disbursementAccountNumber
) {}

