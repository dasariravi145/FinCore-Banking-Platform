package com.fincore.platform.loan.controller;

import com.fincore.platform.common.error.ApiErrorResponse;
import com.fincore.platform.loan.dto.LoanApprovalRequest;
import com.fincore.platform.loan.dto.LoanResponse;
import com.fincore.platform.loan.entity.LoanStatus;
import com.fincore.platform.loan.service.LoanService;
import com.fincore.platform.security.SecurityUtils;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/loans")
@Tag(name = "Admin Loan Management", description = "Endpoints for loan application approval, rejection, and disbursement")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLoanController {

    private final LoanService loanService;

    public AdminLoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping
    @Operation(summary = "List all loans", description = "Retrieves paginated loans across the bank with optional status filtering")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Loans retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Page<LoanResponse>> getAllLoans(
            @RequestParam(required = false) LoanStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(loanService.getAllLoans(pageable, status));
    }

    @PostMapping("/{loanNumber}/review")
    @Operation(summary = "Review loan application", description = "Approves or rejects a loan application with reason")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Loan application reviewed successfully"),
            @ApiResponse(responseCode = "400", description = "Loan not in APPLIED status",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LoanResponse> reviewLoan(
            @PathVariable String loanNumber,
            @Valid @RequestBody LoanApprovalRequest request) {

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        return ResponseEntity.ok(loanService.reviewLoan(loanNumber, request, currentUserId));
    }

    @PostMapping("/{loanNumber}/disburse")
    @Operation(summary = "Disburse approved loan", description = "Disburses funds to borrower's account and activates repayment schedule")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Loan disbursed successfully"),
            @ApiResponse(responseCode = "400", description = "Loan not in APPROVED status or account inactive",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan or account not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LoanResponse> disburseLoan(
            @PathVariable String loanNumber) {

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        return ResponseEntity.ok(loanService.disburseLoan(loanNumber, currentUserId));
    }
}

