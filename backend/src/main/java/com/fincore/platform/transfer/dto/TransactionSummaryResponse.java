package com.fincore.platform.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Summary of a banking transaction for statements and history")
public record TransactionSummaryResponse(
        @Schema(description = "Transaction reference", example = "TX-A1B2C3D4E5F67890")
        String transactionReference,

        @Schema(description = "Source account number", example = "ACC-100000000001")
        String sourceAccountNumber,

        @Schema(description = "Destination account number", example = "ACC-100000000002")
        String destinationAccountNumber,

        @Schema(description = "Transaction amount", example = "1500.00")
        BigDecimal amount,

        @Schema(description = "Currency", example = "INR")
        String currency,

        @Schema(description = "Transaction type", example = "INTERNAL_TRANSFER")
        String type,

        @Schema(description = "Transaction status", example = "COMPLETED")
        String status,

        @Schema(description = "Description", example = "Consulting fee payment")
        String description,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Completion timestamp")
        Instant completedAt
) {}

