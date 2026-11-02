package com.fincore.platform.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Admin request to fund an account from Central System Simulation Reserve")
public record SimulatedFundingRequest(
        @Schema(description = "Destination account number to fund", example = "ACC-100000000001")
        @NotBlank(message = "Destination account number is required")
        String destinationAccountNumber,

        @Schema(description = "Funding amount in INR", example = "50000.00")
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        BigDecimal amount,

        @Schema(description = "Reason or remarks for simulation funding", example = "Initial test seed funding")
        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String description
) {}

