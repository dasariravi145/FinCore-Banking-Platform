package com.fincore.platform.fraud.dto;

import com.fincore.platform.fraud.entity.FraudAssessmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Admin manual review decision for a flagged transaction")
public record FraudReviewRequest(
        @Schema(description = "Status decision (APPROVED or REJECTED)", example = "APPROVED")
        @NotNull(message = "Review status is required")
        FraudAssessmentStatus status,

        @Schema(description = "Review comments/notes", example = "Customer verified via out-of-band call")
        String reviewNotes
) {}

