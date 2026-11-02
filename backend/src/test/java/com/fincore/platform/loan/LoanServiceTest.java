package com.fincore.platform.loan;

import com.fincore.platform.account.entity.AccountStatus;
import com.fincore.platform.account.entity.AccountType;
import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.account.repository.BankAccountRepository;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.InvalidTransferException;
import com.fincore.platform.common.error.UnauthorizedAccountAccessException;
import com.fincore.platform.customer.entity.Customer;
import com.fincore.platform.customer.entity.CustomerStatus;
import com.fincore.platform.customer.repository.CustomerRepository;
import com.fincore.platform.ledger.entity.Transaction;
import com.fincore.platform.ledger.repository.TransactionRepository;
import com.fincore.platform.ledger.service.LedgerService;
import com.fincore.platform.loan.dto.*;
import com.fincore.platform.loan.entity.*;
import com.fincore.platform.loan.repository.LoanPaymentRepository;
import com.fincore.platform.loan.repository.LoanRepaymentScheduleRepository;
import com.fincore.platform.loan.repository.LoanRepository;
import com.fincore.platform.loan.service.LoanService;
import com.fincore.platform.notification.service.NotificationService;
import com.fincore.platform.notification.service.OutboxEventService;
import com.fincore.platform.user.entity.Role;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private LoanRepaymentScheduleRepository scheduleRepository;

    @Mock
    private LoanPaymentRepository paymentRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private SecurityAuditService auditService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private LoanService loanService;

    private User borrowerUser;
    private User adminUser;
    private Customer customer;
    private BankAccount disbursementAccount;
    private BankAccount loanAssetAccount;
    private BankAccount interestIncomeAccount;

    @BeforeEach
    void setUp() {
        borrowerUser = new User("borrower@fincore.local", "hashed", "Rahul", "Dravid", "+919876543100");
        borrowerUser.setId(UUID.randomUUID());
        borrowerUser.setRoles(Set.of(new Role(RoleName.ROLE_CUSTOMER, "Customer")));

        adminUser = new User("admin@fincore.local", "hashed", "Admin", "User", "+919876543101");
        adminUser.setId(UUID.randomUUID());
        adminUser.setRoles(Set.of(new Role(RoleName.ROLE_ADMIN, "Admin")));

        customer = new Customer(borrowerUser, "CUST-001", "Rahul", "Dravid", borrowerUser.getEmail(), "+919876543100");
        customer.setId(UUID.randomUUID());
        customer.setStatus(CustomerStatus.ACTIVE);

        disbursementAccount = new BankAccount(customer, "ACC-DISB-001", AccountType.SAVINGS, "INR");
        disbursementAccount.setId(UUID.randomUUID());
        disbursementAccount.setBalance(BigDecimal.valueOf(10000.00));

        loanAssetAccount = new BankAccount(null, "ACC-LOAN-PORTFOLIO-001", AccountType.LOAN_PORTFOLIO, "INR");
        loanAssetAccount.setId(UUID.randomUUID());
        loanAssetAccount.setBalance(BigDecimal.ZERO);

        interestIncomeAccount = new BankAccount(null, "ACC-INTEREST-INCOME-001", AccountType.INTEREST_INCOME, "INR");
        interestIncomeAccount.setId(UUID.randomUUID());
        interestIncomeAccount.setBalance(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Calculate EMI computes accurate monthly installment and total payable")
    void testCalculateEmi_Accuracy() {
        CalculateEmiRequest request = new CalculateEmiRequest(
                BigDecimal.valueOf(100000.00),
                BigDecimal.valueOf(12.0),
                12
        );

        EmiCalculationResponse response = loanService.calculateEmi(request);

        assertThat(response.principalAmount()).isEqualByComparingTo(BigDecimal.valueOf(100000.00));
        assertThat(response.termMonths()).isEqualTo(12);
        assertThat(response.monthlyEmi()).isEqualByComparingTo(BigDecimal.valueOf(8884.88));
        assertThat(response.totalAmountPayable()).isGreaterThan(response.principalAmount());
        assertThat(response.totalInterestPayable()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Apply for loan succeeds and generates complete amortization schedule")
    void testApplyForLoan_Success() {
        LoanApplicationRequest request = new LoanApplicationRequest(
                LoanType.PERSONAL,
                BigDecimal.valueOf(100000.00),
                12,
                "ACC-DISB-001"
        );

        when(userRepository.findById(borrowerUser.getId())).thenReturn(Optional.of(borrowerUser));
        when(customerRepository.findByUserId(borrowerUser.getId())).thenReturn(Optional.of(customer));
        when(bankAccountRepository.findByAccountNumber("ACC-DISB-001")).thenReturn(Optional.of(disbursementAccount));
        when(loanRepository.save(any(Loan.class))).thenAnswer(i -> {
            Loan l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        LoanResponse response = loanService.applyForLoan(request, borrowerUser.getId());

        assertThat(response).isNotNull();
        assertThat(response.loanNumber()).startsWith("LN-");
        assertThat(response.principalAmount()).isEqualByComparingTo(BigDecimal.valueOf(100000.00));
        assertThat(response.status()).isEqualTo("APPLIED");
        assertThat(response.schedules()).hasSize(12);

        verify(outboxEventService).publishEvent(eq("LOAN"), anyString(), eq("LOAN_APPLIED"), any());
        verify(notificationService).createNotification(eq(borrowerUser), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Apply for loan fails if customer status is not ACTIVE")
    void testApplyForLoan_InactiveCustomerRejected() {
        customer.setStatus(CustomerStatus.SUSPENDED);
        LoanApplicationRequest request = new LoanApplicationRequest(
                LoanType.PERSONAL, BigDecimal.valueOf(50000.00), 12, "ACC-DISB-001"
        );

        when(userRepository.findById(borrowerUser.getId())).thenReturn(Optional.of(borrowerUser));
        when(customerRepository.findByUserId(borrowerUser.getId())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> loanService.applyForLoan(request, borrowerUser.getId()))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessageContaining("Only ACTIVE customers are eligible for loans");
    }

    @Test
    @DisplayName("Apply for loan fails if disbursement account belongs to a different customer")
    void testApplyForLoan_ForeignDisbursementAccountRejected() {
        Customer otherCustomer = new Customer(null, "CUST-OTHER", "Other", "User", "other@test.local", "+919876543299");
        otherCustomer.setId(UUID.randomUUID());
        BankAccount foreignAccount = new BankAccount(otherCustomer, "ACC-FOREIGN-001", AccountType.SAVINGS, "INR");

        LoanApplicationRequest request = new LoanApplicationRequest(
                LoanType.PERSONAL, BigDecimal.valueOf(50000.00), 12, "ACC-FOREIGN-001"
        );

        when(userRepository.findById(borrowerUser.getId())).thenReturn(Optional.of(borrowerUser));
        when(customerRepository.findByUserId(borrowerUser.getId())).thenReturn(Optional.of(customer));
        when(bankAccountRepository.findByAccountNumber("ACC-FOREIGN-001")).thenReturn(Optional.of(foreignAccount));

        assertThatThrownBy(() -> loanService.applyForLoan(request, borrowerUser.getId()))
                .isInstanceOf(UnauthorizedAccountAccessException.class)
                .hasMessageContaining("does not belong to the applicant");
    }

    @Test
    @DisplayName("Admin review approves loan and emits outbox event")
    void testReviewLoan_Approve() {
        Loan loan = new Loan(customer, disbursementAccount, LoanType.PERSONAL, BigDecimal.valueOf(100000.00), BigDecimal.valueOf(12.0), 12, BigDecimal.valueOf(8884.88), BigDecimal.valueOf(6618.56));
        loan.setId(UUID.randomUUID());
        loan.setLoanNumber("LN-TEST-001");

        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(loanRepository.findByLoanNumber("LN-TEST-001")).thenReturn(Optional.of(loan));
        when(loanRepository.save(any(Loan.class))).thenAnswer(i -> i.getArgument(0));

        LoanApprovalRequest request = new LoanApprovalRequest(true, null);
        LoanResponse response = loanService.reviewLoan("LN-TEST-001", request, adminUser.getId());

        assertThat(response.status()).isEqualTo("APPROVED");
        verify(outboxEventService).publishEvent(eq("LOAN"), eq(loan.getId().toString()), eq("LOAN_APPROVED"), any());
    }

    @Test
    @DisplayName("Admin review rejects loan and records rejection reason")
    void testReviewLoan_Reject() {
        Loan loan = new Loan(customer, disbursementAccount, LoanType.PERSONAL, BigDecimal.valueOf(100000.00), BigDecimal.valueOf(12.0), 12, BigDecimal.valueOf(8884.88), BigDecimal.valueOf(6618.56));
        loan.setId(UUID.randomUUID());
        loan.setLoanNumber("LN-TEST-002");

        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(loanRepository.findByLoanNumber("LN-TEST-002")).thenReturn(Optional.of(loan));
        when(loanRepository.save(any(Loan.class))).thenAnswer(i -> i.getArgument(0));

        LoanApprovalRequest request = new LoanApprovalRequest(false, "High debt-to-income ratio");
        LoanResponse response = loanService.reviewLoan("LN-TEST-002", request, adminUser.getId());

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.rejectionReason()).isEqualTo("High debt-to-income ratio");
        verify(outboxEventService).publishEvent(eq("LOAN"), eq(loan.getId().toString()), eq("LOAN_REJECTED"), any());
    }

    @Test
    @DisplayName("Disburse loan posts balanced double entry, credits borrower, and debits loan portfolio asset")
    void testDisburseLoan_Success() {
        Loan loan = new Loan(customer, disbursementAccount, LoanType.PERSONAL, BigDecimal.valueOf(50000.00), BigDecimal.valueOf(12.0), 6, BigDecimal.valueOf(8627.42), BigDecimal.valueOf(1764.52));
        loan.setId(UUID.randomUUID());
        loan.setLoanNumber("LN-TEST-DISB");
        loan.setStatus(LoanStatus.APPROVED);

        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(loanRepository.findByLoanNumber("LN-TEST-DISB")).thenReturn(Optional.of(loan));
        when(bankAccountRepository.findByAccountNumber(LoanService.LOAN_PORTFOLIO_ACCOUNT)).thenReturn(Optional.of(loanAssetAccount));

        when(bankAccountRepository.findByIdWithLock(loanAssetAccount.getId())).thenReturn(Optional.of(loanAssetAccount));
        when(bankAccountRepository.findByIdWithLock(disbursementAccount.getId())).thenReturn(Optional.of(disbursementAccount));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction tx = i.getArgument(0);
            if (tx.getId() == null) tx.setId(UUID.randomUUID());
            return tx;
        });
        when(loanRepository.save(any(Loan.class))).thenAnswer(i -> i.getArgument(0));

        LoanResponse response = loanService.disburseLoan("LN-TEST-DISB", adminUser.getId());

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(ledgerService).postDoubleEntry(
                any(Transaction.class),
                eq(loanAssetAccount),
                eq(disbursementAccount),
                argThat(a -> a != null && a.compareTo(BigDecimal.valueOf(50000.00)) == 0),
                anyString()
        );
        verify(outboxEventService).publishEvent(eq("LOAN"), eq(loan.getId().toString()), eq("LOAN_DISBURSED"), any());
    }

    @Test
    @DisplayName("Disburse loan fails if loan status is not APPROVED")
    void testDisburseLoan_FailsWhenNotApproved() {
        Loan loan = new Loan(customer, disbursementAccount, LoanType.PERSONAL, BigDecimal.valueOf(50000.00), BigDecimal.valueOf(12.0), 6, BigDecimal.valueOf(8627.42), BigDecimal.valueOf(1764.52));
        loan.setStatus(LoanStatus.APPLIED); // not yet approved

        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(loanRepository.findByLoanNumber("LN-TEST-PEND")).thenReturn(Optional.of(loan));

        assertThatThrownBy(() -> loanService.disburseLoan("LN-TEST-PEND", adminUser.getId()))
                .isInstanceOf(InvalidTransferException.class)
                .hasMessageContaining("Only APPROVED loans can be disbursed");
    }

    @Test
    @DisplayName("Repay loan installment allocates payment and posts multi-leg journal")
    void testRepayLoan_PartialRepay() {
        Loan loan = new Loan(customer, disbursementAccount, LoanType.PERSONAL, BigDecimal.valueOf(50000.00), BigDecimal.valueOf(12.0), 6, BigDecimal.valueOf(8627.42), BigDecimal.valueOf(1764.52));
        loan.setId(UUID.randomUUID());
        loan.setLoanNumber("LN-REPAY-001");
        loan.setStatus(LoanStatus.ACTIVE);

        LoanRepaymentSchedule schedule1 = new LoanRepaymentSchedule(1, null, BigDecimal.valueOf(8627.42), BigDecimal.valueOf(8127.42), BigDecimal.valueOf(500.00), BigDecimal.valueOf(41872.58));
        schedule1.setId(UUID.randomUUID());
        loan.addSchedule(schedule1);

        when(userRepository.findById(borrowerUser.getId())).thenReturn(Optional.of(borrowerUser));
        when(loanRepository.findByLoanNumber("LN-REPAY-001")).thenReturn(Optional.of(loan));
        when(bankAccountRepository.findByAccountNumber(disbursementAccount.getAccountNumber())).thenReturn(Optional.of(disbursementAccount));
        when(scheduleRepository.findFirstByLoan_IdAndStatusInOrderByInstallmentNumberAsc(eq(loan.getId()), anyList())).thenReturn(Optional.of(schedule1));

        when(bankAccountRepository.findByAccountNumber(LoanService.LOAN_PORTFOLIO_ACCOUNT)).thenReturn(Optional.of(loanAssetAccount));
        when(bankAccountRepository.findByAccountNumber(LoanService.INTEREST_INCOME_ACCOUNT)).thenReturn(Optional.of(interestIncomeAccount));

        when(bankAccountRepository.findByIdWithLock(disbursementAccount.getId())).thenReturn(Optional.of(disbursementAccount));
        when(bankAccountRepository.findByIdWithLock(loanAssetAccount.getId())).thenReturn(Optional.of(loanAssetAccount));
        when(bankAccountRepository.findByIdWithLock(interestIncomeAccount.getId())).thenReturn(Optional.of(interestIncomeAccount));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction tx = i.getArgument(0);
            if (tx.getId() == null) tx.setId(UUID.randomUUID());
            return tx;
        });

        LoanRepaymentRequest request = new LoanRepaymentRequest(
                BigDecimal.valueOf(8627.42), disbursementAccount.getAccountNumber(), "Installment 1"
        );

        LoanRepaymentReceiptResponse receipt = loanService.repayLoan("LN-REPAY-001", request, borrowerUser.getId());

        assertThat(receipt).isNotNull();
        assertThat(receipt.principalPaid()).isEqualByComparingTo(BigDecimal.valueOf(8127.42));
        assertThat(receipt.interestPaid()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
        assertThat(receipt.totalPaid()).isEqualByComparingTo(BigDecimal.valueOf(8627.42));

        verify(ledgerService).postLoanRepaymentJournal(
                any(),
                eq(disbursementAccount),
                eq(loanAssetAccount),
                eq(interestIncomeAccount),
                argThat(a -> a != null && a.compareTo(BigDecimal.valueOf(8127.42)) == 0),
                argThat(a -> a != null && a.compareTo(BigDecimal.valueOf(500.00)) == 0),
                anyString()
        );
    }

    @Test
    @DisplayName("Full repayment closes loan and updates outstanding principal to zero")
    void testRepayLoan_FullRepaymentClosesLoan() {
        Loan loan = new Loan(customer, disbursementAccount, LoanType.PERSONAL, BigDecimal.valueOf(5000.00), BigDecimal.valueOf(12.0), 1, BigDecimal.valueOf(5050.00), BigDecimal.valueOf(50.00));
        loan.setId(UUID.randomUUID());
        loan.setLoanNumber("LN-FULL-001");
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setOutstandingPrincipal(BigDecimal.valueOf(5000.00));

        LoanRepaymentSchedule schedule = new LoanRepaymentSchedule(1, null, BigDecimal.valueOf(5050.00), BigDecimal.valueOf(5000.00), BigDecimal.valueOf(50.00), BigDecimal.ZERO);
        schedule.setId(UUID.randomUUID());
        loan.addSchedule(schedule);

        when(userRepository.findById(borrowerUser.getId())).thenReturn(Optional.of(borrowerUser));
        when(loanRepository.findByLoanNumber("LN-FULL-001")).thenReturn(Optional.of(loan));
        when(bankAccountRepository.findByAccountNumber(disbursementAccount.getAccountNumber())).thenReturn(Optional.of(disbursementAccount));
        when(scheduleRepository.findFirstByLoan_IdAndStatusInOrderByInstallmentNumberAsc(eq(loan.getId()), anyList())).thenReturn(Optional.of(schedule));

        when(bankAccountRepository.findByAccountNumber(LoanService.LOAN_PORTFOLIO_ACCOUNT)).thenReturn(Optional.of(loanAssetAccount));
        when(bankAccountRepository.findByAccountNumber(LoanService.INTEREST_INCOME_ACCOUNT)).thenReturn(Optional.of(interestIncomeAccount));

        when(bankAccountRepository.findByIdWithLock(disbursementAccount.getId())).thenReturn(Optional.of(disbursementAccount));
        when(bankAccountRepository.findByIdWithLock(loanAssetAccount.getId())).thenReturn(Optional.of(loanAssetAccount));
        when(bankAccountRepository.findByIdWithLock(interestIncomeAccount.getId())).thenReturn(Optional.of(interestIncomeAccount));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction tx = i.getArgument(0);
            if (tx.getId() == null) tx.setId(UUID.randomUUID());
            return tx;
        });

        LoanRepaymentRequest request = new LoanRepaymentRequest(
                BigDecimal.valueOf(5050.00), disbursementAccount.getAccountNumber(), "Full settlement"
        );

        LoanRepaymentReceiptResponse receipt = loanService.repayLoan("LN-FULL-001", request, borrowerUser.getId());

        assertThat(receipt.loanStatus()).isEqualTo("CLOSED");
        assertThat(receipt.remainingOutstandingPrincipal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.CLOSED);
    }
}
