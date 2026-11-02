package com.fincore.platform.account.dto;

import com.fincore.platform.account.entity.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Account balance enquiry response")
public class AccountBalanceResponse {

    @Schema(description = "Account number", example = "ACC-948102391024")
    private String accountNumber;

    @Schema(description = "Currency", example = "INR")
    private String currency;

    @Schema(description = "Cleared available balance", example = "25000.00")
    private BigDecimal availableBalance;

    @Schema(description = "Account status", example = "ACTIVE")
    private AccountStatus status;

    @Schema(description = "Enquiry timestamp")
    private Instant asOf;

    public AccountBalanceResponse() {}

    public AccountBalanceResponse(String accountNumber, String currency, BigDecimal availableBalance, AccountStatus status) {
        this.accountNumber = accountNumber;
        this.currency = currency;
        this.availableBalance = availableBalance;
        this.status = status;
        this.asOf = Instant.now();
    }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
    public void setAvailableBalance(BigDecimal availableBalance) { this.availableBalance = availableBalance; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public Instant getAsOf() { return asOf; }
    public void setAsOf(Instant asOf) { this.asOf = asOf; }
}

