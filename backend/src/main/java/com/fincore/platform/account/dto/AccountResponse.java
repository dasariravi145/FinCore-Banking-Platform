package com.fincore.platform.account.dto;

import com.fincore.platform.account.entity.AccountStatus;
import com.fincore.platform.account.entity.AccountType;
import com.fincore.platform.account.entity.BankAccount;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Bank account summary representation")
public class AccountResponse {

    @Schema(description = "Account UUID identifier", example = "a5e8f492-36c1-482a-9cb0-1ebc90e6b541")
    private UUID id;

    @Schema(description = "12-character unique account number", example = "ACC-948102391024")
    private String accountNumber;

    @Schema(description = "Associated customer number", example = "CUST-98214301")
    private String customerNumber;

    @Schema(description = "Type of account", example = "SAVINGS")
    private AccountType accountType;

    @Schema(description = "Three-letter ISO currency code", example = "INR")
    private String currency;

    @Schema(description = "Current ledger balance (scale 2)", example = "25000.00")
    private BigDecimal balance;

    @Schema(description = "Account operational status", example = "ACTIVE")
    private AccountStatus status;

    @Schema(description = "Account opening timestamp")
    private Instant createdAt;

    public AccountResponse() {}

    public static AccountResponse fromEntity(BankAccount account) {
        AccountResponse res = new AccountResponse();
        res.setId(account.getId());
        res.setAccountNumber(account.getAccountNumber());
        res.setCustomerNumber(account.getCustomer() != null ? account.getCustomer().getCustomerNumber() : "SYSTEM");
        res.setAccountType(account.getAccountType());
        res.setCurrency(account.getCurrency());
        res.setBalance(account.getBalance());
        res.setStatus(account.getStatus());
        res.setCreatedAt(account.getCreatedAt());
        return res;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getCustomerNumber() { return customerNumber; }
    public void setCustomerNumber(String customerNumber) { this.customerNumber = customerNumber; }
    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

