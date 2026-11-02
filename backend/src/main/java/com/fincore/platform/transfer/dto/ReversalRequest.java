package com.fincore.platform.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Admin request payload to reverse a previously completed transaction")
public record ReversalRequest(
        @Schema(description = "Auditable reason for transaction reversal", example = "Customer dispute resolved - unauthorized transfer")
        @NotBlank(message = "Reversal reason is required")
        @Size(min = 5, max = 255, message = "Reversal reason must be between 5 and 255 characters")
        String reason
) {}

