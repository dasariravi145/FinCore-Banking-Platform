package com.fincore.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.platform.account.dto.AccountBalanceResponse;
import com.fincore.platform.account.dto.AccountResponse;
import com.fincore.platform.account.dto.CreateAccountRequest;
import com.fincore.platform.account.dto.UpdateAccountStatusRequest;
import com.fincore.platform.account.entity.AccountStatus;
import com.fincore.platform.account.entity.AccountType;
import com.fincore.platform.ledger.entity.LedgerEntry;
import com.fincore.platform.ledger.entity.Transaction;
import com.fincore.platform.ledger.repository.LedgerEntryRepository;
import com.fincore.platform.ledger.repository.TransactionRepository;
import com.fincore.platform.security.JwtTokenProvider;
import com.fincore.platform.security.UserPrincipal;
import com.fincore.platform.transfer.dto.ReversalRequest;
import com.fincore.platform.transfer.dto.SimulatedFundingRequest;
import com.fincore.platform.transfer.dto.TransferReceiptResponse;
import com.fincore.platform.transfer.dto.TransferRequest;
import com.fincore.platform.customer.dto.CreateCustomerRequest;
import com.fincore.platform.customer.dto.UpdateCustomerRequest;
import com.fincore.platform.user.entity.Role;
import com.fincore.platform.user.entity.RoleName;
import com.fincore.platform.user.entity.User;
import com.fincore.platform.user.repository.RoleRepository;
import com.fincore.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Task 9: Comprehensive Banking Core Integration Tests (Prompt 02)")
class BankingIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BankingIntegrationTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User adminUser;
    private String adminToken;

    private User customerA;
    private String tokenA;

    private User customerB;
    private String tokenB;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_ADMIN, "Admin")));
        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_CUSTOMER, "Customer")));

        // 1. Create or fetch Admin
        adminUser = userRepository.findByEmailIgnoreCase("admin@fincore.test")
                .orElseGet(() -> {
                    User u = new User("admin@fincore.test", passwordEncoder.encode("AdminPass123!"),
                            "Admin", "System", "+919876543200");
                    u.setRoles(Set.of(adminRole));
                    return userRepository.save(u);
                });
        adminToken = jwtTokenProvider.generateToken(UserPrincipal.create(adminUser));

        // 2. Create or fetch Customer A
        customerA = userRepository.findByEmailIgnoreCase("customerA@fincore.test")
                .orElseGet(() -> {
                    User u = new User("customerA@fincore.test", passwordEncoder.encode("CustPass123!"),
                            "Aarav", "Sharma", "+919876543201");
                    u.setRoles(Set.of(customerRole));
                    return userRepository.save(u);
                });
        tokenA = jwtTokenProvider.generateToken(UserPrincipal.create(customerA));

        // 3. Create or fetch Customer B
        customerB = userRepository.findByEmailIgnoreCase("customerB@fincore.test")
                .orElseGet(() -> {
                    User u = new User("customerB@fincore.test", passwordEncoder.encode("CustPass123!"),
                            "Priya", "Patel", "+919876543202");
                    u.setRoles(Set.of(customerRole));
                    return userRepository.save(u);
                });
        tokenB = jwtTokenProvider.generateToken(UserPrincipal.create(customerB));
    }

    @Test
    @DisplayName("Scenarios 1 & 2: Customer Profile Lifecycle and Duplicate Prevention")
    void testCustomerProfileLifecycleAndDuplicatePrevention() throws Exception {
        CreateCustomerRequest createRequest = new CreateCustomerRequest(
                "Aarav", "Sharma", "+919876543210",
                LocalDate.of(1990, 5, 15),
                "123 Marine Drive", "Mumbai", "MH", "400020", "SYNTH-PAN-12345"
        );

        // First creation succeeds or already created
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)));

        // Duplicate profile creation -> 409 Conflict
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // Customer A updates profile
        UpdateCustomerRequest updateRequest = new UpdateCustomerRequest(
                "+919876543299", "456 Worli Sea Face", "Mumbai", "MH", "400030"
        );

        mockMvc.perform(put("/api/v1/customers")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("+919876543299"))
                .andExpect(jsonPath("$.addressLine1").value("456 Worli Sea Face"));
    }

    @Test
    @DisplayName("Scenarios 3 & 4: Account Creation, Ownership & Cross-Customer Protection")
    void testAccountCreationAndOwnershipEnforcement() throws Exception {
        ensureCustomerProfile(tokenA, "Aarav", "Sharma", "+919876543210");
        ensureCustomerProfile(tokenB, "Priya", "Patel", "+919876543211");

        // Customer A opens SAVINGS account
        CreateAccountRequest requestA = new CreateAccountRequest(AccountType.SAVINGS);
        MvcResult resA = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").isNotEmpty())
                .andExpect(jsonPath("$.accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        AccountResponse accountA = objectMapper.readValue(resA.getResponse().getContentAsString(), AccountResponse.class);

        // Customer A can view their own balance
        mockMvc.perform(get("/api/v1/accounts/" + accountA.getAccountNumber() + "/balance")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(0.00));

        // Customer B is strictly forbidden from accessing Customer A's account (403)
        mockMvc.perform(get("/api/v1/accounts/" + accountA.getAccountNumber() + "/balance")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Scenarios 5, 6, 7, 8, 18, 19: Simulated Funding, Internal Transfer, Double-Entry & Reconciliation")
    void testEndToEndTransferFlowWithDoubleEntryAndReconciliation() throws Exception {
        ensureCustomerProfile(tokenA, "Aarav", "Sharma", "+919876543210");
        ensureCustomerProfile(tokenB, "Priya", "Patel", "+919876543211");

        String accNumA = openAccount(tokenA, AccountType.SAVINGS);
        String accNumB = openAccount(tokenB, AccountType.CURRENT);

        // Scenario 5: Admin funds Customer A's account with ₹50,000.00 from Simulation Reserve
        SimulatedFundingRequest fundingRequest = new SimulatedFundingRequest(accNumA, BigDecimal.valueOf(50000.00), "Initial Seed");
        mockMvc.perform(post("/api/v1/admin/simulation/fund-account")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fundingRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(50000.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Verify Customer A balance is now 50000.00
        verifyAccountBalance(tokenA, accNumA, BigDecimal.valueOf(50000.00));

        // Scenario 6: Customer A transfers ₹15,000.00 to Customer B
        String idempotencyKey = UUID.randomUUID().toString();
        TransferRequest transferRequest = new TransferRequest(
                accNumA, accNumB, BigDecimal.valueOf(15000.00), "INR", "Project consulting fee"
        );

        MvcResult transferRes = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(15000.00))
                .andExpect(jsonPath("$.sourceAccountNumber").value(accNumA))
                .andExpect(jsonPath("$.destinationAccountNumber").value(accNumB))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();

        TransferReceiptResponse receipt = objectMapper.readValue(
                transferRes.getResponse().getContentAsString(), TransferReceiptResponse.class);

        // Verify updated balances: A = 35000.00, B = 15000.00
        verifyAccountBalance(tokenA, accNumA, BigDecimal.valueOf(35000.00));
        verifyAccountBalance(tokenB, accNumB, BigDecimal.valueOf(15000.00));

        // Scenario 7 & 8: Verify Double-Entry Ledger Posting & Debit/Credit Equality
        Transaction tx = transactionRepository.findByTransactionReference(receipt.transactionReference()).orElseThrow();
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransaction_Id(tx.getId());
        assertThat(entries).hasSize(2);

        BigDecimal sumDebits = ledgerEntryRepository.sumDebitsByTransactionId(tx.getId());
        BigDecimal sumCredits = ledgerEntryRepository.sumCreditsByTransactionId(tx.getId());

        assertThat(sumDebits).isEqualByComparingTo(BigDecimal.valueOf(15000.00));
        assertThat(sumCredits).isEqualByComparingTo(BigDecimal.valueOf(15000.00));
        assertThat(sumDebits).isEqualByComparingTo(sumCredits); // Strict equality sum(D) == sum(C)

        // Scenario 18: Account Transaction History Pagination
        mockMvc.perform(get("/api/v1/accounts/" + accNumA + "/transactions?page=0&size=10")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2)); // Funding + Transfer

        // Scenario 19: Ledger Balance Reconciliation
        mockMvc.perform(get("/api/v1/accounts/" + accNumA + "/reconciliation")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountBalance").value(35000.00))
                .andExpect(jsonPath("$.ledgerBalance").value(35000.00))
                .andExpect(jsonPath("$.reconciled").value(true))
                .andExpect(jsonPath("$.difference").value(0.00));

        mockMvc.perform(get("/api/v1/accounts/" + accNumB + "/reconciliation")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountBalance").value(15000.00))
                .andExpect(jsonPath("$.ledgerBalance").value(15000.00))
                .andExpect(jsonPath("$.reconciled").value(true));
    }

    @Test
    @DisplayName("Scenarios 9 & 10: Insufficient Balance & Negative/Zero Transfer Rejection")
    void testTransferValidationFailures() throws Exception {
        ensureCustomerProfile(tokenA, "Aarav", "Sharma", "+919876543210");
        ensureCustomerProfile(tokenB, "Priya", "Patel", "+919876543211");

        String accNumA = openAccount(tokenA, AccountType.SAVINGS);
        String accNumB = openAccount(tokenB, AccountType.CURRENT);

        // Account A balance is 0.00 -> Transfer of 1000.00 rejected with 422 (Unprocessable Entity)
        TransferRequest insufficientRequest = new TransferRequest(
                accNumA, accNumB, BigDecimal.valueOf(1000.00), "INR", "Too much"
        );

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(insufficientRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("insufficient")));

        // Zero amount rejected by Bean Validation (400 Bad Request)
        TransferRequest zeroRequest = new TransferRequest(
                accNumA, accNumB, BigDecimal.ZERO, "INR", "Zero amount"
        );
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Negative amount rejected by Bean Validation (400 Bad Request)
        TransferRequest negativeRequest = new TransferRequest(
                accNumA, accNumB, BigDecimal.valueOf(-500.00), "INR", "Negative"
        );
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(negativeRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Scenarios 11 & 12: Idempotency Key Handling & Payload Mismatch Rejection")
    void testIdempotencyHandling() throws Exception {
        ensureCustomerProfile(tokenA, "Aarav", "Sharma", "+919876543210");
        ensureCustomerProfile(tokenB, "Priya", "Patel", "+919876543211");

        String accNumA = openAccount(tokenA, AccountType.SAVINGS);
        String accNumB = openAccount(tokenB, AccountType.CURRENT);

        // Fund Account A with 20000.00
        mockMvc.perform(post("/api/v1/admin/simulation/fund-account")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SimulatedFundingRequest(accNumA, BigDecimal.valueOf(20000.00), "Seed"))))
                .andExpect(status().isCreated());

        String key = "test-idem-key-" + UUID.randomUUID();
        TransferRequest request = new TransferRequest(accNumA, accNumB, BigDecimal.valueOf(5000.00), "INR", "First call");

        // 1. Initial transfer execution
        MvcResult firstCall = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();

        TransferReceiptResponse receipt1 = objectMapper.readValue(firstCall.getResponse().getContentAsString(), TransferReceiptResponse.class);

        // Verify balance after first transfer is 15000.00
        verifyAccountBalance(tokenA, accNumA, BigDecimal.valueOf(15000.00));

        // 2. Scenario 11: Duplicate replay with same key returns cached receipt without re-executing
        MvcResult secondCall = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();

        TransferReceiptResponse receipt2 = objectMapper.readValue(secondCall.getResponse().getContentAsString(), TransferReceiptResponse.class);
        assertThat(receipt2.transactionReference()).isEqualTo(receipt1.transactionReference());

        // Crucial verification: balance is STILL 15000.00 (not debited twice!)
        verifyAccountBalance(tokenA, accNumA, BigDecimal.valueOf(15000.00));

        // 3. Scenario 12: Replay with same key but DIFFERENT amount -> 422 Unprocessable Entity
        TransferRequest modifiedRequest = new TransferRequest(accNumA, accNumB, BigDecimal.valueOf(8000.00), "INR", "Modified amount");
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(modifiedRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("different request parameters")));
    }

    @Test
    @DisplayName("Scenarios 15, 16, 17: Frozen Account Rejection, Invalid Destination & Cross-Customer Statement Denial")
    void testSecurityAndStatusEdgeCases() throws Exception {
        ensureCustomerProfile(tokenA, "Aarav", "Sharma", "+919876543210");
        ensureCustomerProfile(tokenB, "Priya", "Patel", "+919876543211");

        String accNumA = openAccount(tokenA, AccountType.SAVINGS);
        String accNumB = openAccount(tokenB, AccountType.CURRENT);

        // Fund Account A
        mockMvc.perform(post("/api/v1/admin/simulation/fund-account")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SimulatedFundingRequest(accNumA, BigDecimal.valueOf(10000.00), "Seed"))))
                .andExpect(status().isCreated());

        // Scenario 16: Invalid destination account rejection (404)
        TransferRequest invalidDestRequest = new TransferRequest(accNumA, "ACC-NON-EXISTENT-999", BigDecimal.valueOf(1000.00), "INR", "Invalid");
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDestRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Scenario 15: Admin freezes Account A
        mockMvc.perform(patch("/api/v1/admin/accounts/" + accNumA + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAccountStatusRequest(AccountStatus.FROZEN, "Risk investigation"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        // Customer A attempts transfer while frozen -> 422 Unprocessable Entity
        TransferRequest frozenTransfer = new TransferRequest(accNumA, accNumB, BigDecimal.valueOf(1000.00), "INR", "Attempt from frozen");
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(frozenTransfer)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("frozen")));

        // Unfreeze account A
        mockMvc.perform(patch("/api/v1/admin/accounts/" + accNumA + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAccountStatusRequest(AccountStatus.ACTIVE, "Investigation complete"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Scenario 17: Customer B attempts to inspect Customer A's transactions -> 403 Forbidden
        mockMvc.perform(get("/api/v1/accounts/" + accNumA + "/transactions")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Scenario 20: Transaction Reversal Correctness")
    void testTransactionReversal() throws Exception {
        ensureCustomerProfile(tokenA, "Aarav", "Sharma", "+919876543210");
        ensureCustomerProfile(tokenB, "Priya", "Patel", "+919876543211");

        String accNumA = openAccount(tokenA, AccountType.SAVINGS);
        String accNumB = openAccount(tokenB, AccountType.CURRENT);

        // Fund Account A with 10000.00
        mockMvc.perform(post("/api/v1/admin/simulation/fund-account")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SimulatedFundingRequest(accNumA, BigDecimal.valueOf(10000.00), "Seed"))))
                .andExpect(status().isCreated());

        // Customer A transfers 4000.00 to Customer B
        MvcResult txRes = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransferRequest(accNumA, accNumB, BigDecimal.valueOf(4000.00), "INR", "Before reversal"))))
                .andExpect(status().isCreated())
                .andReturn();

        TransferReceiptResponse receipt = objectMapper.readValue(txRes.getResponse().getContentAsString(), TransferReceiptResponse.class);

        // Balances: A = 6000.00, B = 4000.00
        verifyAccountBalance(tokenA, accNumA, BigDecimal.valueOf(6000.00));
        verifyAccountBalance(tokenB, accNumB, BigDecimal.valueOf(4000.00));

        // Admin reverses transaction
        ReversalRequest reversalRequest = new ReversalRequest("Chargeback disputed by customer");
        mockMvc.perform(post("/api/v1/admin/transfers/" + receipt.transactionReference() + "/reverse")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reversalRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("REVERSAL"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Balances should be restored: A = 10000.00, B = 0.00
        verifyAccountBalance(tokenA, accNumA, BigDecimal.valueOf(10000.00));
        verifyAccountBalance(tokenB, accNumB, BigDecimal.valueOf(0.00));

        // Verify account reconciliations after reversal
        mockMvc.perform(get("/api/v1/accounts/" + accNumA + "/reconciliation")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciled").value(true));

        mockMvc.perform(get("/api/v1/accounts/" + accNumB + "/reconciliation")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reconciled").value(true));
    }

    @Test
    @DisplayName("Scenario 13: Concurrent Transfer Simulation without Deadlock or Overdrawing")
    void testConcurrentTransfersDeterministicLocking() throws Exception {
        ensureCustomerProfile(tokenA, "Aarav", "Sharma", "+919876543210");
        ensureCustomerProfile(tokenB, "Priya", "Patel", "+919876543211");

        String accNumA = openAccount(tokenA, AccountType.SAVINGS);
        String accNumB = openAccount(tokenB, AccountType.CURRENT);

        // Fund Account A with 3000.00
        mockMvc.perform(post("/api/v1/admin/simulation/fund-account")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SimulatedFundingRequest(accNumA, BigDecimal.valueOf(3000.00), "Concurrent test seed"))))
                .andExpect(status().isCreated());

        // We launch 3 concurrent transfers of 1000.00 each (Total = 3000.00)
        int threads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                latch.await();
                TransferRequest req = new TransferRequest(accNumA, accNumB, BigDecimal.valueOf(1000.00), "INR", "Concurrent transfer " + index);
                MvcResult result = mockMvc.perform(post("/api/v1/transfers")
                                .header("Authorization", "Bearer " + tokenA)
                                .header("Idempotency-Key", "concurrent-" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andReturn();
                int status = result.getResponse().getStatus();
                if (status == 201) {
                    successCount.incrementAndGet();
                } else {
                    log.warn("Concurrent transfer {} failed with status {}: {}", index, status, result.getResponse().getContentAsString());
                }
                return null;
            }));
        }

        // Trigger all threads simultaneously
        latch.countDown();
        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Transfers that succeeded plus remaining balance must strictly equal initial funds
        MvcResult resA = mockMvc.perform(get("/api/v1/accounts/" + accNumA + "/balance").header("Authorization", "Bearer " + tokenA)).andReturn();
        AccountBalanceResponse balA = objectMapper.readValue(resA.getResponse().getContentAsString(), AccountBalanceResponse.class);

        MvcResult resB = mockMvc.perform(get("/api/v1/accounts/" + accNumB + "/balance").header("Authorization", "Bearer " + tokenB)).andReturn();
        AccountBalanceResponse balB = objectMapper.readValue(resB.getResponse().getContentAsString(), AccountBalanceResponse.class);

        // Verification 1: Balance must NEVER become negative
        assertThat(balA.getAvailableBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        // Verification 2: Total money conserved: balA + balB == 3000.00
        assertThat(balA.getAvailableBalance().add(balB.getAvailableBalance())).isEqualByComparingTo(BigDecimal.valueOf(3000.00));

        // Verification 3: At least one succeeded and all successful transfers match recipient balance
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        BigDecimal expectedRecipientBalance = BigDecimal.valueOf(1000.00).multiply(BigDecimal.valueOf(successCount.get()));
        assertThat(balB.getAvailableBalance()).isEqualByComparingTo(expectedRecipientBalance);
    }

    // Helper utilities

    private void ensureCustomerProfile(String token, String firstName, String lastName, String phone) throws Exception {
        CreateCustomerRequest createRequest = new CreateCustomerRequest(
                firstName, lastName, phone,
                LocalDate.of(1985, 1, 1),
                "100 Banking Street", "Mumbai", "MH", "400001", "SYNTH-PAN-12345"
        );
        mockMvc.perform(post("/api/v1/customers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)));
    }

    private String openAccount(String token, AccountType type) throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(type);
        MvcResult res = mockMvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        AccountResponse response = objectMapper.readValue(res.getResponse().getContentAsString(), AccountResponse.class);
        return response.getAccountNumber();
    }

    private void verifyAccountBalance(String token, String accountNumber, BigDecimal expected) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/accounts/" + accountNumber + "/balance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        AccountBalanceResponse response = objectMapper.readValue(res.getResponse().getContentAsString(), AccountBalanceResponse.class);
        assertThat(response.getAvailableBalance()).isEqualByComparingTo(expected);
    }
}
