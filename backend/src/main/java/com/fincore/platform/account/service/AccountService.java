package com.fincore.platform.account.service;

import com.fincore.platform.account.dto.*;
import com.fincore.platform.account.entity.AccountStatus;
import com.fincore.platform.account.entity.AccountType;
import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.account.repository.BankAccountRepository;
import com.fincore.platform.common.audit.AuditEventType;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.AccountFrozenException;
import com.fincore.platform.common.error.ResourceNotFoundException;
import com.fincore.platform.common.error.UnauthorizedAccountAccessException;
import com.fincore.platform.customer.entity.Customer;
import com.fincore.platform.customer.service.CustomerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AccountService {

    public static final String SYSTEM_RESERVE_ACCOUNT_NUMBER = "ACC-SYSTEM-RESERVE-001";

    private final BankAccountRepository accountRepository;
    private final CustomerService customerService;
    private final SecurityAuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AccountService(BankAccountRepository accountRepository,
                          CustomerService customerService,
                          SecurityAuditService auditService) {
        this.accountRepository = accountRepository;
        this.customerService = customerService;
        this.auditService = auditService;
    }

    @Transactional
    public AccountResponse openAccount(UUID userId, CreateAccountRequest request) {
        Customer customer = customerService.getCustomerEntityByUserId(userId);

        if (customer.getStatus() != com.fincore.platform.customer.entity.CustomerStatus.ACTIVE) {
            throw new AccountFrozenException("Cannot open account for inactive customer profile");
        }

        if (request.getAccountType() == AccountType.SYSTEM_RESERVE) {
            throw new IllegalArgumentException("SYSTEM_RESERVE account cannot be created via customer APIs");
        }

        String accountNumber = generateUniqueAccountNumber();
        BankAccount account = new BankAccount(customer, accountNumber, request.getAccountType(), "INR");

        BankAccount saved = accountRepository.save(account);

        auditService.recordEvent(
                AuditEventType.ACCOUNT_OPENED,
                customer.getEmail(),
                null,
                null,
                "SUCCESS",
                "Opened " + request.getAccountType() + " account " + accountNumber + " for Customer " + customer.getCustomerNumber()
        );

        return AccountResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(UUID userId) {
        Customer customer = customerService.getCustomerEntityByUserId(userId);
        return accountRepository.findAllByCustomerId(customer.getId()).stream()
                .map(AccountResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse getAccountBalance(UUID userId, String accountNumber) {
        BankAccount account = verifyAndGetCustomerAccount(userId, accountNumber);
        return new AccountBalanceResponse(
                account.getAccountNumber(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountDetails(UUID userId, String accountNumber) {
        BankAccount account = verifyAndGetCustomerAccount(userId, accountNumber);
        return AccountResponse.fromEntity(account);
    }

    @Transactional
    public AccountResponse updateAccountStatus(String accountNumber, UpdateAccountStatusRequest request, String adminEmail) {
        BankAccount account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        AccountStatus previousStatus = account.getStatus();
        account.setStatus(request.getStatus());
        BankAccount updated = accountRepository.save(account);

        auditService.recordEvent(
                AuditEventType.ACCOUNT_STATUS_UPDATED,
                adminEmail != null ? adminEmail : "ADMIN",
                null,
                null,
                "SUCCESS",
                "Changed account " + accountNumber + " status from " + previousStatus + " to " + request.getStatus()
                        + ". Reason: " + request.getReason()
        );

        return AccountResponse.fromEntity(updated);
    }

    @Transactional(readOnly = true)
    public BankAccount getSystemReserveAccount() {
        return accountRepository.findByAccountNumber(SYSTEM_RESERVE_ACCOUNT_NUMBER)
                .orElseThrow(() -> new IllegalStateException("System simulation reserve account not found in database"));
    }

    @Transactional(readOnly = true)
    public BankAccount getAccountEntity(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account not found: " + accountNumber));
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> getAllAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable)
                .map(AccountResponse::fromEntity);
    }

    /**
     * Ownership enforcement helper: confirms the target account strictly belongs to the authenticated caller.
     */
    private BankAccount verifyAndGetCustomerAccount(UUID userId, String accountNumber) {
        Customer customer = customerService.getCustomerEntityByUserId(userId);
        BankAccount account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        if (account.getCustomer() == null || !account.getCustomer().getId().equals(customer.getId())) {
            throw new UnauthorizedAccountAccessException("Unauthorized: account " + accountNumber + " does not belong to authenticated customer");
        }

        return account;
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;
        do {
            long randomDigits = 100000000000L + (long) (secureRandom.nextDouble() * 900000000000L);
            accountNumber = "ACC-" + randomDigits;
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}

