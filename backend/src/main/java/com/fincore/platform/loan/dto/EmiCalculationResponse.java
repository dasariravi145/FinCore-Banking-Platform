package com.fincore.platform.loan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Calculated monthly EMI and interest projection")
public record EmiCalculationResponse(
        @Schema(description = "Principal loan amount", example = "500000.00")
        BigDecimal principalAmount,

        @Schema(description = "Annual interest rate percentage", example = "10.50")
        BigDecimal annualInterestRate,

        @Schema(description = "Repayment tenure in months", example = "24")
        Integer termMonths,

        @Schema(description = "Monthly Equated Monthly Installment (EMI)", example = "23190.25")
        BigDecimal monthlyEmi,

        @Schema(description = "Total interest payable over the tenure", example = "56566.00")
        BigDecimal totalInterestPayable,

        @Schema(description = "Total amount payable (Principal + Total Interest)", example = "556566.00")
        BigDecimal totalAmountPayable
) {}

