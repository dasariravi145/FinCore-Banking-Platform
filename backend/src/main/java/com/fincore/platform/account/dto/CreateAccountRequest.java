package com.fincore.platform.account.dto;

import com.fincore.platform.account.entity.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Account opening request payload")
public class CreateAccountRequest {

    @NotNull(message = "Account type is required (SAVINGS or CURRENT)")
    @Schema(description = "Type of banking account to open", example = "SAVINGS")
    private AccountType accountType;

    public CreateAccountRequest() {}

    public CreateAccountRequest(AccountType accountType) {
        this.accountType = accountType;
    }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }
}

