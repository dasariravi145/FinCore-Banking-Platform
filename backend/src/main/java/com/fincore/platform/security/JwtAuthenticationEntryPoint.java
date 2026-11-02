package com.fincore.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincore.platform.common.error.ApiErrorResponse;
import com.fincore.platform.common.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Invoked when an unauthenticated user attempts to access a protected banking resource.
 * Returns a standardized 401 ApiErrorResponse without exposing stack traces.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID_KEY);
        log.warn("Unauthorized request on URI: {} [CID: {}]: {}",
                request.getRequestURI(), correlationId, authException.getMessage());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Full authentication is required to access this banking resource",
                request.getRequestURI(),
                correlationId
        );

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}

