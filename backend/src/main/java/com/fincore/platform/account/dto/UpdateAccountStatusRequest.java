package com.fincore.platform.account.dto;

import com.fincore.platform.account.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Account status update request for administrative freezing or closing")
public class UpdateAccountStatusRequest {

    @NotNull(message = "Target status is required")
    @Schema(description = "New account status (ACTIVE, FROZEN, CLOSED)", example = "FROZEN")
    private AccountStatus status;

    @NotBlank(message = "Reason for status change is required for audit trail")
    @Schema(description = "Justification for administrative action", example = "Fraud prevention review")
    private String reason;

    public UpdateAccountStatusRequest() {}

    public UpdateAccountStatusRequest(AccountStatus status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

