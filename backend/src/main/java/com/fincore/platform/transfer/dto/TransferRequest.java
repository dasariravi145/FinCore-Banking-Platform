package com.fincore.platform.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Request payload for internal funds transfer")
public record TransferRequest(
        @Schema(description = "Source account number", example = "ACC-100000000001")
        @NotBlank(message = "Source account number is required")
        @Size(min = 10, max = 34, message = "Account number must be between 10 and 34 characters")
        String sourceAccountNumber,

        @Schema(description = "Destination account number", example = "ACC-100000000002")
        @NotBlank(message = "Destination account number is required")
        @Size(min = 10, max = 34, message = "Account number must be between 10 and 34 characters")
        String destinationAccountNumber,

        @Schema(description = "Transfer amount (must be positive, scale 2)", example = "1500.00")
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        BigDecimal amount,

        @Schema(description = "3-letter ISO currency code", example = "INR")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
        String currency,

        @Schema(description = "Optional transfer description", example = "Consulting fee payment")
        @Size(max = 255, message = "Description cannot exceed 255 characters")
        String description
) {}

