package com.fincore.platform.account.controller;

import com.fincore.platform.account.dto.AccountBalanceResponse;
import com.fincore.platform.account.dto.AccountResponse;
import com.fincore.platform.account.dto.CreateAccountRequest;
import com.fincore.platform.account.service.AccountService;
import com.fincore.platform.common.error.ApiErrorResponse;
import com.fincore.platform.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "BearerAuth")
@Tag(name = "Account Management", description = "Endpoints for bank account creation, portfolio listing, and balance enquiries")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @Operation(summary = "Open a new bank account", description = "Creates a new SAVINGS or CURRENT account for the authenticated customer")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Account opened successfully",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid account type or parameters",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Customer profile not found for user",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<AccountResponse> openAccount(@Valid @RequestBody CreateAccountRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("SecurityContext did not contain authenticated user ID"));

        AccountResponse response = accountService.openAccount(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my-accounts")
    @Operation(summary = "List all accounts owned by authenticated customer", description = "Returns portfolio of accounts belonging strictly to the caller")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account list returned",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = AccountResponse.class))))
    })
    public ResponseEntity<List<AccountResponse>> getMyAccounts() {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("SecurityContext did not contain authenticated user ID"));

        List<AccountResponse> accounts = accountService.getMyAccounts(currentUserId);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{accountNumber}/balance")
    @Operation(summary = "Enquire account balance", description = "Fetches cleared available balance for a specific account with ownership enforcement")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balance enquiry returned",
                    content = @Content(schema = @Schema(implementation = AccountBalanceResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden: Caller does not own this account",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<AccountBalanceResponse> getBalance(@PathVariable String accountNumber) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("SecurityContext did not contain authenticated user ID"));

        AccountBalanceResponse response = accountService.getAccountBalance(currentUserId, accountNumber);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountNumber}")
    @Operation(summary = "Get account details", description = "Fetches metadata for a specific account with ownership enforcement")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account details returned",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden: Caller does not own this account",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<AccountResponse> getAccountDetails(@PathVariable String accountNumber) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("SecurityContext did not contain authenticated user ID"));

        AccountResponse response = accountService.getAccountDetails(currentUserId, accountNumber);
        return ResponseEntity.ok(response);
    }
}

