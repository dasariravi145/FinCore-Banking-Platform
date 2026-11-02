package com.fincore.platform.loan.service;

import com.fincore.platform.account.entity.AccountStatus;
import com.fincore.platform.account.entity.AccountType;
import com.fincore.platform.account.entity.BankAccount;
import com.fincore.platform.account.repository.BankAccountRepository;
import com.fincore.platform.common.audit.AuditEventType;
import com.fincore.platform.common.audit.SecurityAuditService;
import com.fincore.platform.common.error.InsufficientFundsException;
import com.fincore.platform.common.error.InvalidTransferException;
import com.fincore.platform.common.error.ResourceNotFoundException;
import com.fincore.platform.common.error.UnauthorizedAccountAccessException;
import com.fincore.platform.customer.entity.Customer;
import com.fincore.platform.customer.entity.CustomerStatus;
import com.fincore.platform.customer.repository.CustomerRepository;
import com.fincore.platform.ledger.entity.Transaction;
import com.fincore.platform.ledger.entity.TransactionType;
import com.fincore.platform.ledger.repository.TransactionRepository;
import com.fincore.platform.ledger.service.LedgerService;
import com.fincore.platform.loan.dto.*;
import com.fincore.platform.loan.entity.*;
import com.fincore.platform.loan.repository.LoanPaymentRepository;
import com.fincore.platform.loan.repository.LoanRepaymentScheduleRepository;
import com.fincore.platform.loan.repository.LoanRepository;
import com.fincore.platform.notification.entity.NotificationType;
import com.fincore.platform.notification.service.NotificationService;
import com.fincore.platform.notification.service.OutboxEventService;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    public static final String LOAN_PORTFOLIO_ACCOUNT = "ACC-LOAN-PORTFOLIO-001";
    public static final String INTEREST_INCOME_ACCOUNT = "ACC-INTEREST-INCOME-001";

    private static final Map<LoanType, BigDecimal> DEFAULT_INTEREST_RATES = Map.of(
            LoanType.PERSONAL, BigDecimal.valueOf(12.00),
            LoanType.HOME, BigDecimal.valueOf(8.50),
            LoanType.AUTO, BigDecimal.valueOf(9.50),
            LoanType.BUSINESS, BigDecimal.valueOf(11.00)
    );

    private final LoanRepository loanRepository;
    private final LoanRepaymentScheduleRepository scheduleRepository;
    private final LoanPaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final BankAccountRepository bankAccountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;
    private final SecurityAuditService auditService;
    private final NotificationService notificationService;
    private final OutboxEventService outboxEventService;

    public LoanService(LoanRepository loanRepository,
                       LoanRepaymentScheduleRepository scheduleRepository,
                       LoanPaymentRepository paymentRepository,
                       CustomerRepository customerRepository,
                       BankAccountRepository bankAccountRepository,
                       TransactionRepository transactionRepository,
                       UserRepository userRepository,
                       LedgerService ledgerService,
                       SecurityAuditService auditService,
                       NotificationService notificationService,
                       OutboxEventService outboxEventService) {
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.outboxEventService = outboxEventService;
    }

    /**
     * Calculates monthly EMI, total interest, and total payable amount.
     */
    public EmiCalculationResponse calculateEmi(CalculateEmiRequest request) {
        BigDecimal emi = computeMonthlyEmi(request.principalAmount(), request.annualInterestRate(), request.termMonths());
        BigDecimal totalPayable = emi.multiply(BigDecimal.valueOf(request.termMonths())).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal totalInterest = totalPayable.subtract(request.principalAmount()).setScale(2, RoundingMode.HALF_EVEN);

        return new EmiCalculationResponse(
                request.principalAmount().setScale(2, RoundingMode.HALF_EVEN),
                request.annualInterestRate().setScale(2, RoundingMode.HALF_EVEN),
                request.termMonths(),
                emi,
                totalInterest,
                totalPayable
        );
    }

    /**
     * Customer applies for a synthetic loan.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanResponse applyForLoan(LoanApplicationRequest request, UUID customerUserId) {
        User currentUser = userRepository.findById(customerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + customerUserId));

        Customer customer = customerRepository.findByUserId(customerUserId)
                .orElseThrow(() -> new InvalidTransferException("Customer profile does not exist. Complete profile first."));

        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw new InvalidTransferException("Customer status is " + customer.getStatus() + ". Only ACTIVE customers are eligible for loans.");
        }

        BankAccount disbursementAccount = bankAccountRepository.findByAccountNumber(request.disbursementAccountNumber().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Disbursement account not found: " + request.disbursementAccountNumber()));

        if (disbursementAccount.getCustomer() == null || !disbursementAccount.getCustomer().getId().equals(customer.getId())) {
            throw new UnauthorizedAccountAccessException("Disbursement account does not belong to the applicant");
        }

        if (disbursementAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransferException("Disbursement account is " + disbursementAccount.getStatus() + ". Account must be ACTIVE.");
        }

        BigDecimal interestRate = DEFAULT_INTEREST_RATES.getOrDefault(request.loanType(), BigDecimal.valueOf(10.00));
        BigDecimal emi = computeMonthlyEmi(request.principalAmount(), interestRate, request.termMonths());
        BigDecimal totalPayable = emi.multiply(BigDecimal.valueOf(request.termMonths())).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal totalInterest = totalPayable.subtract(request.principalAmount()).setScale(2, RoundingMode.HALF_EVEN);

        Loan loan = new Loan(
                customer,
                disbursementAccount,
                request.loanType(),
                request.principalAmount(),
                interestRate,
                request.termMonths(),
                emi,
                totalInterest
        );

        String loanNumber = "LN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        loan.setLoanNumber(loanNumber);

        // Build Amortization Schedule
        generateAmortizationSchedule(loan, request.principalAmount(), interestRate, request.termMonths(), emi);

        Loan savedLoan = loanRepository.save(loan);

        auditService.recordEvent(
                AuditEventType.LOAN_APPLIED,
                currentUser.getEmail(),
                "127.0.0.1",
                "FinCore-Backend",
                "SUCCESS",
                "Loan applied: " + loanNumber + " (" + request.loanType() + ", Principal: " + request.principalAmount() + ")"
        );

        outboxEventService.publishEvent("LOAN", savedLoan.getId().toString(), "LOAN_APPLIED", Map.of(
                "loanNumber", loanNumber,
                "customerNumber", customer.getCustomerNumber(),
                "principal", request.principalAmount()
        ));

        notificationService.createNotification(
                currentUser,
                "Loan Application Submitted",
                "Your application for loan " + loanNumber + " of ₹" + request.principalAmount() + " has been submitted for review.",
                NotificationType.LOAN_APPLIED
        );

        return toLoanResponse(savedLoan);
    }

    /**
     * Admin reviews and approves or rejects a loan.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanResponse reviewLoan(String loanNumber, LoanApprovalRequest request, UUID adminUserId) {
        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + adminUserId));

        Loan loan = loanRepository.findByLoanNumber(loanNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanNumber));

        if (loan.getStatus() != LoanStatus.APPLIED) {
            throw new InvalidTransferException("Loan " + loanNumber + " is in status " + loan.getStatus() + ". Only APPLIED loans can be reviewed.");
        }

        User borrowerUser = loan.getCustomer().getUser();

        if (Boolean.TRUE.equals(request.approved())) {
            loan.setStatus(LoanStatus.APPROVED);
            loan.setApprovedAt(Instant.now());
            loan.setApprovedBy(adminUser);
            loan.setRejectionReason(null);

            auditService.recordEvent(
                    AuditEventType.LOAN_APPROVED,
                    adminUser.getEmail(),
                    "127.0.0.1",
                    "FinCore-Backend",
                    "SUCCESS",
                    "Loan approved: " + loanNumber + " by admin " + adminUser.getEmail()
            );

            outboxEventService.publishEvent("LOAN", loan.getId().toString(), "LOAN_APPROVED", Map.of(
                    "loanNumber", loanNumber,
                    "approvedBy", adminUser.getEmail()
            ));

            if (borrowerUser != null) {
                notificationService.createNotification(
                        borrowerUser,
                        "Loan Approved",
                        "Congratulations! Your loan " + loanNumber + " for ₹" + loan.getPrincipalAmount() + " has been approved.",
                        NotificationType.LOAN_APPROVED
                );
            }
        } else {
            loan.setStatus(LoanStatus.REJECTED);
            loan.setRejectionReason(request.rejectionReason() != null ? request.rejectionReason() : "Loan application rejected by administration");

            auditService.recordEvent(
                    AuditEventType.LOAN_REJECTED,
                    adminUser.getEmail(),
                    "127.0.0.1",
                    "FinCore-Backend",
                    "SUCCESS",
                    "Loan rejected: " + loanNumber + ". Reason: " + loan.getRejectionReason()
            );

            outboxEventService.publishEvent("LOAN", loan.getId().toString(), "LOAN_REJECTED", Map.of(
                    "loanNumber", loanNumber,
                    "reason", loan.getRejectionReason()
            ));

            if (borrowerUser != null) {
                notificationService.createNotification(
                        borrowerUser,
                        "Loan Application Rejected",
                        "Your loan application " + loanNumber + " was not approved. Reason: " + loan.getRejectionReason(),
                        NotificationType.LOAN_REJECTED
                );
            }
        }

        Loan saved = loanRepository.save(loan);
        return toLoanResponse(saved);
    }

    /**
     * Admin disburses an approved loan to the borrower's account with double-entry ledger posting.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanResponse disburseLoan(String loanNumber, UUID adminUserId) {
        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + adminUserId));

        Loan loan = loanRepository.findByLoanNumber(loanNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanNumber));

        if (loan.getStatus() != LoanStatus.APPROVED) {
            throw new InvalidTransferException("Loan " + loanNumber + " is in status " + loan.getStatus() + ". Only APPROVED loans can be disbursed.");
        }

        BankAccount loanAssetAccount = bankAccountRepository.findByAccountNumber(LOAN_PORTFOLIO_ACCOUNT)
                .orElseThrow(() -> new ResourceNotFoundException("System loan portfolio account not found: " + LOAN_PORTFOLIO_ACCOUNT));

        BankAccount borrowerAccount = loan.getDisbursementAccount();
        if (borrowerAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransferException("Borrower disbursement account " + borrowerAccount.getAccountNumber() + " is " + borrowerAccount.getStatus());
        }

        // Deterministic lock acquisition between loan asset account and borrower account
        UUID firstId = loanAssetAccount.getId().compareTo(borrowerAccount.getId()) < 0 ? loanAssetAccount.getId() : borrowerAccount.getId();
        UUID secondId = loanAssetAccount.getId().compareTo(borrowerAccount.getId()) < 0 ? borrowerAccount.getId() : loanAssetAccount.getId();

        BankAccount lockedFirst = bankAccountRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Lock acquisition failed: " + firstId));
        BankAccount lockedSecond = bankAccountRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Lock acquisition failed: " + secondId));

        BankAccount lockedLoanAsset = loanAssetAccount.getId().equals(firstId) ? lockedFirst : lockedSecond;
        BankAccount lockedBorrower = borrowerAccount.getId().equals(firstId) ? lockedFirst : lockedSecond;

        // Create Transaction record for loan disbursement
        String txRef = "TX-LN-DISB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Transaction tx = new Transaction(
                txRef,
                lockedLoanAsset,
                lockedBorrower,
                loan.getPrincipalAmount(),
                loanAssetAccount.getCurrency(),
                TransactionType.LOAN_DISBURSEMENT,
                null,
                "Disbursement of loan " + loan.getLoanNumber(),
                adminUser
        );
        tx = transactionRepository.save(tx);

        // Post balanced double-entry:
        // Debited: ACC-LOAN-PORTFOLIO-001 (Asset increases)
        // Credited: Borrower Bank Account (Liability increases)
        ledgerService.postDoubleEntry(tx, lockedLoanAsset, lockedBorrower, loan.getPrincipalAmount(), tx.getDescription());
        tx.markCompleted();
        transactionRepository.save(tx);

        // Update loan status to ACTIVE / DISBURSED
        loan.setStatus(LoanStatus.ACTIVE);
        loan.setDisbursedAt(Instant.now());

        // Update schedule due dates starting from next month
        LocalDate baseDate = LocalDate.now();
        List<LoanRepaymentSchedule> schedules = loan.getSchedules();
        for (int i = 0; i < schedules.size(); i++) {
            schedules.get(i).setDueDate(baseDate.plusMonths(i + 1L));
        }

        Loan saved = loanRepository.save(loan);

        auditService.recordEvent(
                AuditEventType.LOAN_DISBURSED,
                adminUser.getEmail(),
                "127.0.0.1",
                "FinCore-Backend",
                "SUCCESS",
                "Loan disbursed: " + loanNumber + " (₹" + loan.getPrincipalAmount() + ") to account " +
                        lockedBorrower.getAccountNumber() + " (TX: " + txRef + ")"
        );

        outboxEventService.publishEvent("LOAN", saved.getId().toString(), "LOAN_DISBURSED", Map.of(
                "loanNumber", loanNumber,
                "amount", loan.getPrincipalAmount(),
                "destinationAccount", lockedBorrower.getAccountNumber()
        ));

        if (loan.getCustomer().getUser() != null) {
            notificationService.createNotification(
                    loan.getCustomer().getUser(),
                    "Loan Disbursed",
                    "₹" + loan.getPrincipalAmount() + " has been credited to your account " +
                            lockedBorrower.getAccountNumber() + " for loan " + loanNumber + ".",
                    NotificationType.LOAN_DISBURSED
            );
        }

        return toLoanResponse(saved);
    }

    /**
     * Customer makes a repayment payment towards their active loan.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanRepaymentReceiptResponse repayLoan(String loanNumber, LoanRepaymentRequest request, UUID customerUserId) {
        User currentUser = userRepository.findById(customerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + customerUserId));

        Loan loan = loanRepository.findByLoanNumber(loanNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanNumber));

        boolean isAdmin = currentUser.getRoles().stream().anyMatch(r -> RoleName.ROLE_ADMIN.equals(r.getName()));
        if (!isAdmin) {
            if (loan.getCustomer() == null || !loan.getCustomer().getUser().getId().equals(customerUserId)) {
                throw new UnauthorizedAccountAccessException("User does not have access to loan: " + loanNumber);
            }
        }

        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.OVERDUE && loan.getStatus() != LoanStatus.DISBURSED) {
            throw new InvalidTransferException("Loan " + loanNumber + " cannot be repaid in status: " + loan.getStatus());
        }

        if (loan.getOutstandingPrincipal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Loan " + loanNumber + " is already fully repaid");
        }

        BigDecimal paymentAmount = request.amount().setScale(2, RoundingMode.HALF_EVEN);
        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Repayment amount must be strictly greater than zero");
        }

        BankAccount sourceAccount = bankAccountRepository.findByAccountNumber(request.sourceAccountNumber().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Source account not found: " + request.sourceAccountNumber()));

        if (!isAdmin) {
            if (sourceAccount.getCustomer() == null || !sourceAccount.getCustomer().getUser().getId().equals(customerUserId)) {
                throw new UnauthorizedAccountAccessException("User is not authorized to repay from account: " + sourceAccount.getAccountNumber());
            }
        }

        if (sourceAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransferException("Source account is " + sourceAccount.getStatus() + ". Account must be ACTIVE.");
        }

        // Find the oldest pending or partially paid schedule installment
        LoanRepaymentSchedule schedule = scheduleRepository.findFirstByLoan_IdAndStatusInOrderByInstallmentNumberAsc(
                loan.getId(), List.of(ScheduleStatus.PENDING, ScheduleStatus.PARTIALLY_PAID)
        ).orElse(null);

        // Calculate principal vs interest breakdown
        BigDecimal interestPaid;
        BigDecimal principalPaid;

        if (schedule != null) {
            BigDecimal remainingInterestInInstallment = schedule.getInterestComponent().subtract(schedule.getInterestPaid());
            if (paymentAmount.compareTo(remainingInterestInInstallment) <= 0) {
                // Payment covers interest only or partial interest
                interestPaid = paymentAmount;
                principalPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
            } else {
                interestPaid = remainingInterestInInstallment;
                BigDecimal remainingPayment = paymentAmount.subtract(interestPaid);
                // Principal cannot exceed total outstanding principal
                principalPaid = remainingPayment.min(loan.getOutstandingPrincipal()).setScale(2, RoundingMode.HALF_EVEN);
            }
        } else {
            // No schedule remaining; entire payment goes towards outstanding principal
            interestPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
            principalPaid = paymentAmount.min(loan.getOutstandingPrincipal()).setScale(2, RoundingMode.HALF_EVEN);
        }

        BigDecimal totalPayment = principalPaid.add(interestPaid).setScale(2, RoundingMode.HALF_EVEN);

        BankAccount loanAssetAccount = bankAccountRepository.findByAccountNumber(LOAN_PORTFOLIO_ACCOUNT)
                .orElseThrow(() -> new ResourceNotFoundException("System loan portfolio account not found: " + LOAN_PORTFOLIO_ACCOUNT));
        BankAccount interestIncomeAccount = bankAccountRepository.findByAccountNumber(INTEREST_INCOME_ACCOUNT)
                .orElseThrow(() -> new ResourceNotFoundException("System interest income account not found: " + INTEREST_INCOME_ACCOUNT));

        // Lock accounts in deterministic order
        List<UUID> accountIdsToLock = new ArrayList<>(List.of(
                sourceAccount.getId(), loanAssetAccount.getId(), interestIncomeAccount.getId()
        ));
        Collections.sort(accountIdsToLock);

        Map<UUID, BankAccount> lockedMap = new HashMap<>();
        for (UUID id : accountIdsToLock) {
            BankAccount locked = bankAccountRepository.findByIdWithLock(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Account lock acquisition failed: " + id));
            lockedMap.put(id, locked);
        }

        BankAccount lockedSource = lockedMap.get(sourceAccount.getId());
        BankAccount lockedLoanAsset = lockedMap.get(loanAssetAccount.getId());
        BankAccount lockedInterestIncome = lockedMap.get(interestIncomeAccount.getId());

        if (lockedSource.getBalance().compareTo(totalPayment) < 0) {
            throw new InsufficientFundsException("Account " + lockedSource.getAccountNumber() + " has insufficient funds for loan repayment");
        }

        // Post balanced multi-leg repayment journal
        String txRef = "TX-LN-REPAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Transaction tx = new Transaction(
                txRef,
                lockedSource,
                lockedLoanAsset,
                totalPayment,
                lockedSource.getCurrency(),
                TransactionType.LOAN_REPAYMENT,
                null,
                "Repayment for loan " + loan.getLoanNumber() + (request.description() != null ? ": " + request.description() : ""),
                currentUser
        );
        tx = transactionRepository.save(tx);

        ledgerService.postLoanRepaymentJournal(
                tx, lockedSource, lockedLoanAsset, lockedInterestIncome,
                principalPaid, interestPaid, tx.getDescription()
        );
        tx.markCompleted();
        transactionRepository.save(tx);

        // Update schedule if present
        if (schedule != null) {
            schedule.setInterestPaid(schedule.getInterestPaid().add(interestPaid));
            schedule.setPrincipalPaid(schedule.getPrincipalPaid().add(principalPaid));
            BigDecimal totalInstallmentPaid = schedule.getPrincipalPaid().add(schedule.getInterestPaid());
            if (totalInstallmentPaid.compareTo(schedule.getEmiAmount()) >= 0) {
                schedule.setStatus(ScheduleStatus.PAID);
                schedule.setPaidAt(Instant.now());
            } else {
                schedule.setStatus(ScheduleStatus.PARTIALLY_PAID);
            }
            scheduleRepository.save(schedule);
        }

        // Update loan totals
        loan.setTotalPrincipalPaid(loan.getTotalPrincipalPaid().add(principalPaid));
        loan.setTotalInterestPaid(loan.getTotalInterestPaid().add(interestPaid));
        BigDecimal newOutstanding = loan.getOutstandingPrincipal().subtract(principalPaid).setScale(2, RoundingMode.HALF_EVEN);
        loan.setOutstandingPrincipal(newOutstanding.max(BigDecimal.ZERO));

        if (loan.getOutstandingPrincipal().compareTo(BigDecimal.ZERO) == 0) {
            loan.setStatus(LoanStatus.CLOSED);
            loan.setClosedAt(Instant.now());
        }
        loanRepository.save(loan);

        // Record loan payment entry
        String paymentRef = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        LoanPayment payment = new LoanPayment(
                loan, schedule, tx, lockedSource, totalPayment, principalPaid, interestPaid, paymentRef
        );
        paymentRepository.save(payment);

        auditService.recordEvent(
                AuditEventType.LOAN_REPAID,
                currentUser.getEmail(),
                "127.0.0.1",
                "FinCore-Backend",
                "SUCCESS",
                "Loan repaid: " + loanNumber + " (Total: ₹" + totalPayment + ", Principal: ₹" +
                        principalPaid + ", Interest: ₹" + interestPaid + ") (TX: " + txRef + ")"
        );

        outboxEventService.publishEvent("LOAN", loan.getId().toString(), "LOAN_REPAID", Map.of(
                "loanNumber", loanNumber,
                "paymentReference", paymentRef,
                "amount", totalPayment,
                "remainingPrincipal", loan.getOutstandingPrincipal()
        ));

        notificationService.createNotification(
                currentUser,
                "Loan Repayment Received",
                "Repayment of ₹" + totalPayment + " received for loan " + loanNumber + ". Remaining principal: ₹" + loan.getOutstandingPrincipal(),
                NotificationType.LOAN_REPAYMENT
        );

        return new LoanRepaymentReceiptResponse(
                paymentRef,
                loan.getLoanNumber(),
                lockedSource.getAccountNumber(),
                totalPayment,
                principalPaid,
                interestPaid,
                loan.getOutstandingPrincipal(),
                loan.getStatus().name(),
                payment.getPaidAt(),
                loan.getStatus() == LoanStatus.CLOSED ? "Loan has been fully paid off and CLOSED." : "Repayment successful."
        );
    }

    @Transactional(readOnly = true)
    public LoanResponse getLoanByLoanNumber(String loanNumber, UUID userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Loan loan = loanRepository.findByLoanNumber(loanNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanNumber));

        boolean isAdmin = currentUser.getRoles().stream().anyMatch(r -> RoleName.ROLE_ADMIN.equals(r.getName()));
        if (!isAdmin) {
            if (loan.getCustomer() == null || !loan.getCustomer().getUser().getId().equals(userId)) {
                throw new UnauthorizedAccountAccessException("User does not have access to loan: " + loanNumber);
            }
        }

        return toLoanResponse(loan);
    }

    @Transactional(readOnly = true)
    public Page<LoanResponse> getMyLoans(UUID customerUserId, Pageable pageable) {
        Customer customer = customerRepository.findByUserId(customerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for user: " + customerUserId));

        return loanRepository.findByCustomer_IdOrderByAppliedAtDesc(customer.getId(), pageable)
                .map(this::toLoanResponse);
    }

    @Transactional(readOnly = true)
    public Page<LoanResponse> getAllLoans(Pageable pageable, LoanStatus status) {
        if (status != null) {
            return loanRepository.findByStatusOrderByAppliedAtDesc(status, pageable).map(this::toLoanResponse);
        }
        return loanRepository.findAll(pageable).map(this::toLoanResponse);
    }

    private void generateAmortizationSchedule(Loan loan, BigDecimal principal, BigDecimal annualRate, int termMonths, BigDecimal emi) {
        BigDecimal remainingPrincipal = principal.setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_EVEN);
        LocalDate dueDate = LocalDate.now();

        for (int i = 1; i <= termMonths; i++) {
            dueDate = dueDate.plusMonths(1);
            BigDecimal interestComponent = remainingPrincipal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal principalComponent;
            BigDecimal scheduledEmi = emi;

            if (i == termMonths) {
                // Final installment adjustments to ensure exact balance matching
                principalComponent = remainingPrincipal;
                scheduledEmi = principalComponent.add(interestComponent).setScale(2, RoundingMode.HALF_EVEN);
                remainingPrincipal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
            } else {
                principalComponent = scheduledEmi.subtract(interestComponent).setScale(2, RoundingMode.HALF_EVEN);
                remainingPrincipal = remainingPrincipal.subtract(principalComponent).setScale(2, RoundingMode.HALF_EVEN);
            }

            LoanRepaymentSchedule schedule = new LoanRepaymentSchedule(
                    i,
                    dueDate,
                    scheduledEmi,
                    principalComponent,
                    interestComponent,
                    remainingPrincipal
            );
            loan.addSchedule(schedule);
        }
    }

    private BigDecimal computeMonthlyEmi(BigDecimal principal, BigDecimal annualRate, int termMonths) {
        if (annualRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_EVEN);
        }
        double r = annualRate.doubleValue() / 1200.0;
        double p = principal.doubleValue();
        double factor = Math.pow(1.0 + r, termMonths);
        double emi = (p * r * factor) / (factor - 1.0);
        return BigDecimal.valueOf(emi).setScale(2, RoundingMode.HALF_EVEN);
    }

    private LoanResponse toLoanResponse(Loan loan) {
        List<RepaymentScheduleResponse> scheduleResponses = loan.getSchedules().stream()
                .map(s -> new RepaymentScheduleResponse(
                        s.getId(),
                        s.getInstallmentNumber(),
                        s.getDueDate(),
                        s.getEmiAmount(),
                        s.getPrincipalComponent(),
                        s.getInterestComponent(),
                        s.getOutstandingBalanceAfter(),
                        s.getPrincipalPaid(),
                        s.getInterestPaid(),
                        s.getStatus().name(),
                        s.getPaidAt()
                ))
                .toList();

        return new LoanResponse(
                loan.getId(),
                loan.getLoanNumber(),
                loan.getCustomer().getCustomerNumber(),
                loan.getCustomer().getFirstName() + " " + loan.getCustomer().getLastName(),
                loan.getDisbursementAccount().getAccountNumber(),
                loan.getLoanType().name(),
                loan.getPrincipalAmount(),
                loan.getInterestRate(),
                loan.getTermMonths(),
                loan.getMonthlyEmi(),
                loan.getTotalInterestCharged(),
                loan.getOutstandingPrincipal(),
                loan.getTotalPrincipalPaid(),
                loan.getTotalInterestPaid(),
                loan.getStatus().name(),
                loan.getRejectionReason(),
                loan.getAppliedAt(),
                loan.getApprovedAt(),
                loan.getDisbursedAt(),
                loan.getClosedAt(),
                scheduleResponses
        );
    }
}

