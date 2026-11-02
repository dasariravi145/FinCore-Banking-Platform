package com.fincore.platform.loan.controller;

import com.fincore.platform.common.error.ApiErrorResponse;
import com.fincore.platform.loan.dto.*;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/loans")
@Tag(name = "Loans", description = "Customer loan application, schedule tracking, and repayments")
@SecurityRequirement(name = "bearerAuth")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping("/calculate-emi")
    @Operation(summary = "Calculate loan EMI quote", description = "Calculates monthly EMI and total interest for a prospective loan amount, rate, and tenure")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "EMI calculation quote returned"),
            @ApiResponse(responseCode = "400", description = "Invalid calculation parameters",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<EmiCalculationResponse> calculateEmi(
            @Valid @RequestBody CalculateEmiRequest request) {
        return ResponseEntity.ok(loanService.calculateEmi(request));
    }

    @PostMapping("/apply")
    @Operation(summary = "Apply for a loan", description = "Submits a new loan application for an active customer and generates the amortization schedule")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Loan application submitted successfully"),
            @ApiResponse(responseCode = "400", description = "Validation or eligibility error",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Disbursement account not owned by customer",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LoanResponse> applyForLoan(
            @Valid @RequestBody LoanApplicationRequest request) {

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        LoanResponse response = loanService.applyForLoan(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my-loans")
    @Operation(summary = "List customer loans", description = "Retrieves paginated loans applied by or issued to the authenticated customer")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customer loans retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Page<LoanResponse>> getMyLoans(
            @PageableDefault(size = 10) Pageable pageable) {

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        return ResponseEntity.ok(loanService.getMyLoans(currentUserId, pageable));
    }

    @GetMapping("/{loanNumber}")
    @Operation(summary = "Get loan details and repayment schedule", description = "Retrieves full details of a loan including its complete amortization schedule")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Loan details retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - access denied to loan",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LoanResponse> getLoanDetails(
            @PathVariable String loanNumber) {

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        return ResponseEntity.ok(loanService.getLoanByLoanNumber(loanNumber, currentUserId));
    }

    @PostMapping("/{loanNumber}/repay")
    @Operation(summary = "Repay loan installment", description = "Executes an installment repayment payment against the active loan with double-entry ledger allocation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Repayment processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid loan status or repayment amount",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Loan or account not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Insufficient funds in source account",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LoanRepaymentReceiptResponse> repayLoan(
            @PathVariable String loanNumber,
            @Valid @RequestBody LoanRepaymentRequest request) {

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        return ResponseEntity.ok(loanService.repayLoan(loanNumber, request, currentUserId));
    }
}

