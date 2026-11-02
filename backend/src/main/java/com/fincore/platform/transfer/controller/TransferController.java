package com.fincore.platform.transfer.controller;

import com.fincore.platform.common.error.ApiErrorResponse;
import com.fincore.platform.ledger.service.TransactionService;
import com.fincore.platform.security.SecurityUtils;
import com.fincore.platform.transfer.dto.TransferReceiptResponse;
import com.fincore.platform.transfer.dto.TransferRequest;
import com.fincore.platform.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Funds Transfers", description = "Internal fund transfer operations with double-entry ledger posting and idempotency")
@SecurityRequirement(name = "BearerAuth")
public class TransferController {

    private final TransferService transferService;
    private final TransactionService transactionService;

    public TransferController(TransferService transferService,
                              TransactionService transactionService) {
        this.transferService = transferService;
        this.transactionService = transactionService;
    }

    @PostMapping
    @Operation(summary = "Transfer funds between accounts", description = "Executes an internal transfer with deterministic locking, balance checks, double-entry ledger posting, and SHA-256 idempotency protection.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Transfer completed successfully",
                    content = @Content(schema = @Schema(implementation = TransferReceiptResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request or insufficient funds",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Unauthorized account access",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Idempotency key payload mismatch",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<TransferReceiptResponse> executeTransfer(
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        TransferReceiptResponse receipt = transferService.transferFunds(request, idempotencyKey, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    }

    @GetMapping("/{transactionReference}")
    @Operation(summary = "Get transfer receipt", description = "Retrieves transaction receipt by reference for authorized account holders or admins.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Receipt found",
                    content = @Content(schema = @Schema(implementation = TransferReceiptResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied to transaction receipt",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Transaction not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<TransferReceiptResponse> getTransferReceipt(
            @PathVariable String transactionReference) {

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        TransferReceiptResponse receipt = transactionService.getReceiptByReference(transactionReference, currentUserId);
        return ResponseEntity.ok(receipt);
    }
}
