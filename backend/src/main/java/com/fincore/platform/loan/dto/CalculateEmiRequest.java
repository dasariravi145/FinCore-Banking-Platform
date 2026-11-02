package com.fincore.platform.loan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Request to calculate monthly EMI and loan amortization breakdown")
public record CalculateEmiRequest(
        @Schema(description = "Principal loan amount in INR", example = "500000.00")
        @NotNull(message = "Principal amount is required")
        @DecimalMin(value = "1000.00", message = "Principal amount must be at least ₹1,000.00")
        @DecimalMax(value = "10000000.00", message = "Principal amount cannot exceed ₹10,000,000.00")
        BigDecimal principalAmount,

        @Schema(description = "Annual interest rate percentage", example = "10.5")
        @NotNull(message = "Interest rate is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "Interest rate must be non-negative")
        @DecimalMax(value = "40.0", message = "Interest rate cannot exceed 40%")
        BigDecimal annualInterestRate,

        @Schema(description = "Repayment tenure in months", example = "24")
        @NotNull(message = "Tenure in months is required")
        @Min(value = 1, message = "Tenure must be at least 1 month")
        @Max(value = 360, message = "Tenure cannot exceed 360 months (30 years)")
        Integer termMonths
) {}

