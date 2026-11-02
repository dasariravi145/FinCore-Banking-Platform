package com.fincore.platform.transfer.controller;

import com.fincore.platform.common.error.ApiErrorResponse;
import com.fincore.platform.ledger.dto.ReconciliationResult;
import com.fincore.platform.ledger.service.TransactionService;
import com.fincore.platform.security.SecurityUtils;
import com.fincore.platform.transfer.dto.TransactionSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts/{accountNumber}")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Transaction History & Reconciliation", description = "Query account transaction history, statements, and ledger balance reconciliation")
@SecurityRequirement(name = "BearerAuth")
public class TransactionHistoryController {

    private final TransactionService transactionService;

    public TransactionHistoryController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get account transaction history", description = "Returns paginated list of debits and credits for an account within optional date range.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction history retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied to account history",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Page<TransactionSummaryResponse>> getTransactionHistory(
            @PathVariable String accountNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(page = 0, size = 20) Pageable pageable) {

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        Page<TransactionSummaryResponse> history = transactionService.getAccountTransactions(
                accountNumber, from, to, pageable, currentUserId);

        return ResponseEntity.ok(history);
    }

    @GetMapping("/reconciliation")
    @Operation(summary = "Reconcile account balance against double-entry ledger", description = "Calculates aggregate sum of all debits and credits from immutable ledger and compares with current balance.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reconciliation result",
                    content = @Content(schema = @Schema(implementation = ReconciliationResult.class))),
            @ApiResponse(responseCode = "403", description = "Access denied to account reconciliation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<ReconciliationResult> reconcileAccountBalance(
            @PathVariable String accountNumber) {

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        ReconciliationResult result = transactionService.reconcileAccount(accountNumber, currentUserId);
        return ResponseEntity.ok(result);
    }
}
