package com.fincore.platform.transfer.controller;

import com.fincore.platform.common.error.ApiErrorResponse;
import com.fincore.platform.ledger.service.TransactionService;
import com.fincore.platform.security.SecurityUtils;
import com.fincore.platform.transfer.dto.ReversalRequest;
import com.fincore.platform.transfer.dto.SimulatedFundingRequest;
import com.fincore.platform.transfer.dto.TransactionSummaryResponse;
import com.fincore.platform.transfer.dto.TransferReceiptResponse;
import com.fincore.platform.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Banking Operations", description = "Privileged banking operations including simulated funding, reversals, and audit inspection")
@SecurityRequirement(name = "BearerAuth")
public class AdminTransferController {

    private final TransferService transferService;
    private final TransactionService transactionService;

    public AdminTransferController(TransferService transferService,
                                   TransactionService transactionService) {
        this.transferService = transferService;
        this.transactionService = transactionService;
    }

    @PostMapping("/simulation/fund-account")
    @Operation(summary = "Fund synthetic account (Simulation Only)", description = "Debits Central Simulation Reserve and credits customer account with balanced double-entry ledger entries.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Account funded successfully",
                    content = @Content(schema = @Schema(implementation = TransferReceiptResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or inactive account",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (Admin only)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Destination account not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<TransferReceiptResponse> fundAccountSimulated(
            @Valid @RequestBody SimulatedFundingRequest request) {

        UUID adminUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated admin not found"));

        TransferReceiptResponse receipt = transferService.fundAccountSimulated(request, adminUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    }

    @PostMapping("/transfers/{transactionReference}/reverse")
    @Operation(summary = "Reverse transaction", description = "Reverses a completed transfer by creating balanced compensating ledger entries.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction reversed successfully",
                    content = @Content(schema = @Schema(implementation = TransferReceiptResponse.class))),
            @ApiResponse(responseCode = "400", description = "Transaction cannot be reversed or already reversed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (Admin only)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transaction not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<TransferReceiptResponse> reverseTransaction(
            @PathVariable String transactionReference,
            @Valid @RequestBody ReversalRequest request) {

        UUID adminUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated admin not found"));

        TransferReceiptResponse receipt = transferService.reverseTransaction(transactionReference, request, adminUserId);
        return ResponseEntity.ok(receipt);
    }

    @GetMapping("/transactions")
    @Operation(summary = "List all platform transactions", description = "Admin endpoint to view all system transactions with pagination.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction list retrieved"),
            @ApiResponse(responseCode = "403", description = "Access denied (Admin only)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Page<TransactionSummaryResponse>> getAllTransactions(
            @PageableDefault(page = 0, size = 20) Pageable pageable) {

        Page<TransactionSummaryResponse> transactions = transactionService.getAllTransactions(pageable);
        return ResponseEntity.ok(transactions);
    }
}
