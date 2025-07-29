package com.dome.quotemanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Filter to handle correlation IDs for distributed tracing across services.
 * 
 * This filter:
 * - Extracts correlation ID from incoming request headers (X-Correlation-ID)
 * - Generates a new UUID if no correlation ID is present
 * - Stores the correlation ID in SLF4J MDC for the duration of the request
 * - Adds the correlation ID to the response headers for downstream services
 * 
 * The correlation ID appears in all log messages for easy request tracing.
 */
@Slf4j
@Component
@Order(0) // Execute before RequestLoggingFilter
public class CorrelationIdFilter extends OncePerRequestFilter {
    
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_CORRELATION_ID_KEY = "correlationId";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        // Extract or generate correlation ID
        String correlationId = extractOrGenerateCorrelationId(request);
        
        try {
            // Store in MDC for logging
            MDC.put(MDC_CORRELATION_ID_KEY, correlationId);
            
            // Add to response headers for downstream services
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            // Log the correlation ID for this request
            log.debug("[CORRELATION] Processing request with correlation ID: {}", correlationId);
            
            // Continue with the filter chain
            filterChain.doFilter(request, response);
            
        } finally {
            // Always clean up MDC to prevent memory leaks
            MDC.remove(MDC_CORRELATION_ID_KEY);
        }
    }
    
    /**
     * Extracts correlation ID from request headers or generates a new one
     */
    private String extractOrGenerateCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        
        if (correlationId == null || correlationId.trim().isEmpty()) {
            // Generate new correlation ID if none provided
            correlationId = UUID.randomUUID().toString();
            log.debug("[NEW] Generated new correlation ID: {}", correlationId);
        } else {
            log.debug("[INCOMING] Using incoming correlation ID: {}", correlationId);
        }
        
        return correlationId;
    }
    
    /**
     * Utility method to get current correlation ID from any part of the application
     */
    public static String getCurrentCorrelationId() {
        return MDC.get(MDC_CORRELATION_ID_KEY);
    }
}