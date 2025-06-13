package com.dome.quotemanagement.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@Order(1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
        "/actuator", "/health", "/swagger", "/api-docs", "/webjars"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        // Skip logging for health checks and static resources
        if (shouldSkipLogging(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Wrap request and response to capture content
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        
        try {
            // Log incoming request
            logIncomingRequest(wrappedRequest);
            
            // Process the request
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            
            // Log outgoing response
            logOutgoingResponse(wrappedRequest, wrappedResponse, System.currentTimeMillis() - startTime);
            
        } finally {
            // Important: Copy response content back
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void logIncomingRequest(ContentCachingRequestWrapper request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        
        log.info("=== INCOMING REQUEST === {} {} {}",
                method, uri, queryString != null ? "?" + queryString : "");
        
        // Log headers (selective)
        log.debug("Request Headers: Content-Type={}, User-Agent={}, Authorization={}",
                request.getHeader("Content-Type"),
                request.getHeader("User-Agent"),
                request.getHeader("Authorization") != null ? "***PROVIDED***" : "null");
        
        // Log request body for POST/PUT/PATCH operations
        if (hasRequestBody(method)) {
            String requestBody = getRequestBody(request);
            if (!requestBody.isEmpty()) {
                log.info("Request Payload: {}", requestBody);
            }
        }
    }

    private void logOutgoingResponse(ContentCachingRequestWrapper request, 
                                   ContentCachingResponseWrapper response, 
                                   long duration) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();
        
        log.info("=== OUTGOING RESPONSE === {} {} - Status: {} - Duration: {}ms",
                method, uri, status, duration);
        
        // Log response headers
        log.debug("Response Headers: Content-Type={}, Content-Length={}",
                response.getHeader("Content-Type"),
                response.getHeader("Content-Length"));
        
        // Log response body for successful operations (avoid logging large responses)
        if (status >= 200 && status < 400) {
            String responseBody = getResponseBody(response);
            if (!responseBody.isEmpty() && responseBody.length() < 5000) { // Limit size
                log.info("Response Payload: {}", responseBody);
            } else if (responseBody.length() >= 5000) {
                log.info("Response Payload: [LARGE_RESPONSE_TRUNCATED] Size: {} characters", responseBody.length());
            }
        } else {
            // Always log error responses
            String responseBody = getResponseBody(response);
            if (!responseBody.isEmpty()) {
                log.error("Error Response Payload: {}", responseBody);
            }
        }
    }

    private boolean shouldSkipLogging(String uri) {
        return EXCLUDED_PATHS.stream().anyMatch(uri::startsWith);
    }

    private boolean hasRequestBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        try {
            byte[] content = request.getContentAsByteArray();
            if (content.length > 0) {
                return new String(content, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Failed to read request body: {}", e.getMessage());
        }
        return "";
    }

    private String getResponseBody(ContentCachingResponseWrapper response) {
        try {
            byte[] content = response.getContentAsByteArray();
            if (content.length > 0) {
                return new String(content, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Failed to read response body: {}", e.getMessage());
        }
        return "";
    }
} 