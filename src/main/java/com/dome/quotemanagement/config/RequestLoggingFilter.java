package com.dome.quotemanagement.config;

import com.dome.quotemanagement.util.LogFormatUtil;
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
@Order(1) // Execute after CorrelationIdFilter
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
        "/actuator", "/health", "/swagger", "/api-docs", "/webjars", "/favicon.ico"
    );
    
    private static final int MAX_LOG_LENGTH = 3000; // For consistent formatting

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
            
        } catch (Exception ex) {
            log.error("[ERROR] Exception during request processing: {}", ex.getMessage(), ex);
            throw ex;
        } finally {
            // Important: Copy response content back
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void logIncomingRequest(ContentCachingRequestWrapper request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUrl = uri + (queryString != null ? "?" + queryString : "");
        
        // Create formatted request header
        String formattedRequest = LogFormatUtil.formatHttpRequest(method, fullUrl);
        log.info("{}", LogFormatUtil.createLogSection("INCOMING REQUEST"));
        log.info("{}", formattedRequest);
        
        // Log headers (selective with masking)
        log.debug("[HEADERS] Request Headers: Content-Type={}, User-Agent={}, Authorization={}",
                request.getHeader("Content-Type"),
                request.getHeader("User-Agent"),
                request.getHeader("Authorization") != null ? "[PROVIDED]" : "null");
        
        // Log request body for POST/PUT/PATCH operations
        if (hasRequestBody(method)) {
            String requestBody = getRequestBody(request);
            if (!requestBody.isEmpty()) {
                String formattedJson = LogFormatUtil.formatJsonForLog(requestBody, MAX_LOG_LENGTH);
                log.info("[PAYLOAD] Request Payload:\n{}", formattedJson);
            }
        }
        
        log.info("{}", LogFormatUtil.createLogSectionEnd());
    }

    private void logOutgoingResponse(ContentCachingRequestWrapper request, 
                                   ContentCachingResponseWrapper response, 
                                   long duration) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();
        
        // Create formatted response header
        String formattedResponse = LogFormatUtil.formatHttpResponse(method, uri, status, duration);
        log.info("{}", LogFormatUtil.createLogSection("OUTGOING RESPONSE"));
        log.info("{}", formattedResponse);
        
        // Log response headers
        log.debug("[HEADERS] Response Headers: Content-Type={}, Content-Length={}",
                response.getHeader("Content-Type"),
                response.getHeader("Content-Length"));
        
        // Log response body with appropriate formatting
        String responseBody = getResponseBody(response);
        if (!responseBody.isEmpty()) {
            if (status >= 200 && status < 400) {
                // Success response - use pretty formatting
                String formattedJson = LogFormatUtil.formatJsonForLog(responseBody, MAX_LOG_LENGTH);
                log.info("[PAYLOAD] Response Payload:\n{}", formattedJson);
                
                // Always log full response in DEBUG for success cases
                if (responseBody.length() > MAX_LOG_LENGTH) {
                    log.debug("[DEBUG] Full Response (DEBUG):\n{}", LogFormatUtil.prettifyJson(responseBody));
                }
            } else {
                // Error response - always log fully and prettified
                log.error("[ERROR] Error Response Payload:\n{}", LogFormatUtil.prettifyJson(responseBody));
            }
        }
        
        log.info("{}", LogFormatUtil.createLogSectionEnd());
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
                String body = new String(content, StandardCharsets.UTF_8);
                // Apply basic masking for sensitive content
                return LogFormatUtil.maskSensitiveData(body, "request_body");
            }
        } catch (Exception e) {
            log.warn("[WARN] Failed to read request body: {}", e.getMessage());
        }
        return "";
    }

    private String getResponseBody(ContentCachingResponseWrapper response) {
        try {
            byte[] content = response.getContentAsByteArray();
            if (content.length > 0) {
                String body = new String(content, StandardCharsets.UTF_8);
                // Apply basic masking for sensitive content
                return LogFormatUtil.maskSensitiveData(body, "response_body");
            }
        } catch (Exception e) {
            log.warn("[WARN] Failed to read response body: {}", e.getMessage());
        }
        return "";
    }
} 