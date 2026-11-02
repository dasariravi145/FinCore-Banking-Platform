package com.fincore.platform.loan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Admin review decision for loan application")
public record LoanApprovalRequest(
        @Schema(description = "Decision flag: true to approve, false to reject", example = "true")
        @NotNull(message = "Decision (approved) is required")
        Boolean approved,

        @Schema(description = "Reason for rejection if approved is false", example = "Insufficient repayment capacity")
        String rejectionReason
) {}

