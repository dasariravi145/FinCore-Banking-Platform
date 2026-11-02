package com.fincore.platform.fraud.controller;

import com.fincore.platform.common.error.ApiErrorResponse;
import com.fincore.platform.fraud.dto.FraudAssessmentResponse;
import com.fincore.platform.fraud.dto.FraudReviewRequest;
import com.fincore.platform.fraud.entity.FraudAssessmentStatus;
import com.fincore.platform.fraud.entity.FraudDecision;
import com.fincore.platform.fraud.service.FraudDetectionService;
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
@RequestMapping("/api/v1/admin/fraud")
@Tag(name = "Admin Fraud Management", description = "Endpoints for fraud risk assessments and review workflows")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFraudController {

    private final FraudDetectionService fraudDetectionService;

    public AdminFraudController(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @GetMapping("/assessments")
    @Operation(summary = "List fraud assessments", description = "Retrieves paginated list of fraud assessments with optional filtering by decision or status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Assessments retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Page<FraudAssessmentResponse>> getAssessments(
            @RequestParam(required = false) FraudDecision decision,
            @RequestParam(required = false) FraudAssessmentStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(fraudDetectionService.getAssessments(pageable, decision, status));
    }

    @PostMapping("/assessments/{id}/review")
    @Operation(summary = "Review fraud assessment", description = "Allows compliance officer/admin to review and decide on a flagged assessment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Assessment reviewed successfully"),
            @ApiResponse(responseCode = "404", description = "Assessment not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<FraudAssessmentResponse> reviewAssessment(
            @PathVariable UUID id,
            @Valid @RequestBody FraudReviewRequest request) {

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

        return ResponseEntity.ok(fraudDetectionService.reviewAssessment(id, request, currentUserId));
    }
}

