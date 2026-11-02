package com.fincore.platform.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Centralized exception handler for FinCore Banking Platform.
 * Ensures consistent error envelopes, strips internal stack traces,
 * and maintains audit-safe correlation IDs.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_ID_KEY = "correlationId";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        String correlationId = getOrCreateCorrelationId();
        log.warn("Validation error on {}: {}", request.getRequestURI(), fieldErrors);

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for one or more fields",
                request.getRequestURI(),
                correlationId,
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleUserAlreadyExists(
            UserAlreadyExistsException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("User registration conflict on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.info("Resource not found on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        // Constant-time and non-enumerating safe message
        log.warn("Authentication failed for request to {}", request.getRequestURI());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Invalid email or password",
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiErrorResponse> handleLockedAccount(
            LockedException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("Locked account access attempt on {}", request.getRequestURI());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Account is temporarily locked due to multiple failed login attempts. Please try again later.",
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabledAccount(
            DisabledException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("Disabled account access attempt on {}", request.getRequestURI());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Account is inactive or suspended. Please contact customer support.",
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<ApiErrorResponse> handleTokenRefreshException(
            TokenRefreshException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("Token refresh rejected on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Access denied: insufficient permissions to access this banking resource",
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("Rate limit triggered on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.error("Database constraint violation on {}: {}", request.getRequestURI(), ex.getMessage());

        // Mask internal SQL schema details from client response
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "Operation could not be completed due to a database constraint or duplicate record",
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("Authentication failed on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Authentication required or credentials invalid",
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientFunds(
            InsufficientFundsException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("Insufficient funds for transfer on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(AccountFrozenException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountFrozen(
            AccountFrozenException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("Frozen account transfer rejection on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(AccountClosedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountClosed(
            AccountClosedException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("Closed account transfer rejection on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(UnauthorizedAccountAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorizedAccountAccess(
            UnauthorizedAccountAccessException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("Unauthorized account access attempt on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(IdempotencyPayloadMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyMismatch(
            IdempotencyPayloadMismatchException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("Idempotency key payload mismatch on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(InvalidTransferException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTransfer(
            InvalidTransferException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("Invalid transfer request on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(FraudBlockedException.class)
    public ResponseEntity<ApiErrorResponse> handleFraudBlocked(
            FraudBlockedException ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.warn("Transfer blocked by fraud detection on {}: {}", request.getRequestURI(), ex.getMessage());

        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        String correlationId = getOrCreateCorrelationId();
        log.error("Unhandled server exception [CID: {}] on {}: {}",
                correlationId, request.getRequestURI(), ex.getMessage(), ex);

        // Security requirement: Never expose stack trace or internal details
        ApiErrorResponse response = new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected internal error occurred. Reference ID: " + correlationId,
                request.getRequestURI(),
                correlationId
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private String getOrCreateCorrelationId() {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }
        return correlationId;
    }
}

