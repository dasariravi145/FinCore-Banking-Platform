package com.fincore.platform.account;

import com.fincore.platform.account.dto.AccountBalanceResponse;
import com.fincore.platform.account.dto.AccountResponse;
import com.fincore.platform.account.dto.CreateAccountRequest;
import com.fincore.platform.account.dto.UpdateAccountStatusRequest;
import com.fincore.platform.account.entity.AccountStatus;
import com.fincore.platform.account.entity.AccountType;
import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.account.repository.BankAccountRepository;
import com.fincore.platform.account.service.AccountService;
import com.fincore.platform.common.audit.AuditEventType;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.AccountFrozenException;
import com.fincore.platform.common.error.UnauthorizedAccountAccessException;
import com.fincore.platform.customer.entity.Customer;
import com.fincore.platform.customer.entity.CustomerStatus;
import com.fincore.platform.customer.service.CustomerService;
import com.fincore.platform.user.entity.Role;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private BankAccountRepository accountRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private SecurityAuditService auditService;

    @InjectMocks
    private AccountService accountService;

    private User user;
    private Customer customer;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User("customer@fincore.local", "hashedPassword", "Aarav", "Sharma", "+919876543210");
        user.setId(userId);
        user.setRoles(Set.of(new Role(RoleName.ROLE_CUSTOMER, "Customer")));

        customer = new Customer(user, "CUST-12345678", "Aarav", "Sharma", user.getEmail(), "+919876543210");
        customer.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Open savings account successfully")
    void testOpenAccount_Success() {
        CreateAccountRequest request = new CreateAccountRequest(AccountType.SAVINGS);

        when(customerService.getCustomerEntityByUserId(userId)).thenReturn(customer);
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(BankAccount.class))).thenAnswer(i -> {
            BankAccount acc = i.getArgument(0);
            acc.setId(UUID.randomUUID());
            return acc;
        });

        AccountResponse response = accountService.openAccount(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getAccountType()).isEqualTo(AccountType.SAVINGS);
        assertThat(response.getCurrency()).isEqualTo("INR");
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.getAccountNumber()).startsWith("ACC-");

        verify(accountRepository, times(1)).save(any(BankAccount.class));
        verify(auditService, times(1)).recordEvent(
                eq(AuditEventType.ACCOUNT_OPENED),
                eq(user.getEmail()),
                any(), any(), eq("SUCCESS"), any()
        );
    }

    @Test
    @DisplayName("Open account fails if customer is suspended or inactive")
    void testOpenAccount_CustomerInactive() {
        customer.setStatus(CustomerStatus.SUSPENDED);
        when(customerService.getCustomerEntityByUserId(userId)).thenReturn(customer);

        CreateAccountRequest request = new CreateAccountRequest(AccountType.SAVINGS);

        assertThatThrownBy(() -> accountService.openAccount(userId, request))
                .isInstanceOf(AccountFrozenException.class)
                .hasMessageContaining("Cannot open account for inactive customer profile");
    }

    @Test
    @DisplayName("Enquire account balance successfully when owned by user")
    void testGetBalance_Success() {
        BankAccount account = new BankAccount(customer, "ACC-100000000001", AccountType.SAVINGS, "INR");
        account.setBalance(BigDecimal.valueOf(5000.00));
        when(accountRepository.findByAccountNumber("ACC-100000000001")).thenReturn(Optional.of(account));
        when(customerService.getCustomerEntityByUserId(userId)).thenReturn(customer);

        AccountBalanceResponse balanceResponse = accountService.getAccountBalance(userId, "ACC-100000000001");

        assertThat(balanceResponse.getAccountNumber()).isEqualTo("ACC-100000000001");
        assertThat(balanceResponse.getAvailableBalance()).isEqualByComparingTo(BigDecimal.valueOf(5000.00));
        assertThat(balanceResponse.getCurrency()).isEqualTo("INR");
    }

    @Test
    @DisplayName("Enquire account balance rejected when accessed by unauthorized user")
    void testGetBalance_UnauthorizedAccess() {
        BankAccount account = new BankAccount(customer, "ACC-100000000001", AccountType.SAVINGS, "INR");
        account.setBalance(BigDecimal.valueOf(5000.00));
        when(accountRepository.findByAccountNumber("ACC-100000000001")).thenReturn(Optional.of(account));

        Customer intruderCustomer = new Customer(null, "CUST-INTRUDER", "Intruder", "User", "intruder@test.local", "+919876543299");
        intruderCustomer.setId(UUID.randomUUID());
        UUID intruderUserId = UUID.randomUUID();
        when(customerService.getCustomerEntityByUserId(intruderUserId)).thenReturn(intruderCustomer);

        assertThatThrownBy(() -> accountService.getAccountBalance(intruderUserId, "ACC-100000000001"))
                .isInstanceOf(UnauthorizedAccountAccessException.class)
                .hasMessageContaining("does not belong to authenticated customer");
    }

    @Test
    @DisplayName("Admin freezes and unfreezes account successfully")
    void testAdminUpdateAccountStatus_FreezeAndUnfreeze() {
        BankAccount account = new BankAccount(customer, "ACC-100000000001", AccountType.SAVINGS, "INR");
        account.setBalance(BigDecimal.valueOf(5000.00));
        when(accountRepository.findByAccountNumber("ACC-100000000001")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(BankAccount.class))).thenAnswer(i -> i.getArgument(0));

        // 1. Freeze
        UpdateAccountStatusRequest freezeRequest = new UpdateAccountStatusRequest(AccountStatus.FROZEN, "Suspected fraud");
        AccountResponse frozenResponse = accountService.updateAccountStatus("ACC-100000000001", freezeRequest, "admin@fincore.local");
        assertThat(frozenResponse.getStatus()).isEqualTo(AccountStatus.FROZEN);

        // 2. Unfreeze
        UpdateAccountStatusRequest unfreezeRequest = new UpdateAccountStatusRequest(AccountStatus.ACTIVE, "Verification cleared");
        AccountResponse activeResponse = accountService.updateAccountStatus("ACC-100000000001", unfreezeRequest, "admin@fincore.local");
        assertThat(activeResponse.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        verify(auditService, times(2)).recordEvent(
                eq(AuditEventType.ACCOUNT_STATUS_UPDATED),
                eq("admin@fincore.local"),
                any(), any(), eq("SUCCESS"), any()
        );
    }
}
