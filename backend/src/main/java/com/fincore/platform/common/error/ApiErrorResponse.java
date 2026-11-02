package com.fincore.platform.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized API Error Response contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standardized FinCore error response envelope")
public class ApiErrorResponse {

    @Schema(description = "UTC timestamp when the error occurred", example = "2026-11-01T16:30:00Z")
    private Instant timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "HTTP error phrase", example = "Bad Request")
    private String error;

    @Schema(description = "Descriptive error message", example = "Validation failed for one or more fields")
    private String message;

    @Schema(description = "Requested resource path", example = "/api/v1/auth/login")
    private String path;

    @Schema(description = "Correlation ID for request tracing and log correlation", example = "c89f2a41-3b7c-4a34-9182-d419b12a8f82")
    private String correlationId;

    @Schema(description = "Field-level validation error details, if applicable")
    private Map<String, String> fieldErrors;

    public ApiErrorResponse() {
        this.timestamp = Instant.now();
    }

    public ApiErrorResponse(int status, String error, String message, String path, String correlationId) {
        this.timestamp = Instant.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
        this.correlationId = correlationId;
    }

    public ApiErrorResponse(int status, String error, String message, String path, String correlationId, Map<String, String> fieldErrors) {
        this(status, error, message, path, correlationId);
        this.fieldErrors = fieldErrors;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public void setFieldErrors(Map<String, String> fieldErrors) {
        this.fieldErrors = fieldErrors;
    }
}

