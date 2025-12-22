package com.dome.quotemanagement.config;

import com.dome.quotemanagement.util.LogFormatUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class RestTemplateLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final int MAX_LOG_LENGTH = 3000; // Reduced for better readability

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                                      ClientHttpRequestExecution execution) throws IOException {
        
        long startTime = System.currentTimeMillis();
        
        // Add correlation ID to outgoing request headers
        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            request.getHeaders().add("X-Correlation-ID", correlationId);
        }
        
        // Log outgoing request to TMForum
        logTMForumRequest(request, body);
        
        ClientHttpResponse response = null;
        try {
            // Execute the request
            response = execution.execute(request, body);
            
            // Create a buffered response that can be read multiple times
            BufferedClientHttpResponse bufferedResponse = new BufferedClientHttpResponse(response);
            
            // Log the response from TMForum
            logTMForumResponse(request, bufferedResponse, System.currentTimeMillis() - startTime);
            
            return bufferedResponse;
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            String formattedError = LogFormatUtil.formatHttpResponse(
                request.getMethod().toString(), 
                request.getURI().toString(), 
                500, 
                duration
            );
            log.error("[ERROR] TMForum API ERROR: {} - {}", formattedError, e.getMessage());
            throw e;
        }
    }

    private void logTMForumRequest(HttpRequest request, byte[] body) {
        String method = request.getMethod().toString();
        String url = request.getURI().toString();
        
        // Create formatted request header
        String formattedRequest = LogFormatUtil.formatHttpRequest(method, url);
        log.info("{}", LogFormatUtil.createLogSection("TMForum OUTGOING REQUEST"));
        log.info("{}", formattedRequest);
        
        // Log request headers (selective)
        String authHeader = request.getHeaders().getFirst("Authorization");
        String authStatus = authHeader != null ? "[PROVIDED]" : "[MISSING]";
        log.debug("[HEADERS] Request Headers: Content-Type={}, Accept={}, Authorization={}", 
                request.getHeaders().getFirst("Content-Type"),
                request.getHeaders().getFirst("Accept"),
                authStatus);
        
        // Log request body for operations that have a body
        if (body != null && body.length > 0) {
            String requestBody = new String(body, StandardCharsets.UTF_8);
            String formattedJson = LogFormatUtil.formatJsonForLog(requestBody, MAX_LOG_LENGTH);
            log.info("[PAYLOAD] Request Payload:\n{}", formattedJson);
        }
        
        log.info("{}", LogFormatUtil.createLogSectionEnd());
    }

    private void logTMForumResponse(HttpRequest request, ClientHttpResponse response, long duration) {
        try {
            String method = request.getMethod().toString();
            String url = request.getURI().toString();
            int statusCode = response.getStatusCode().value();
            
            // Create formatted response header
            String formattedResponse = LogFormatUtil.formatHttpResponse(method, url, statusCode, duration);
            log.info("{}", LogFormatUtil.createLogSection("TMForum INCOMING RESPONSE"));
            log.info("{}", formattedResponse);
            
            // Log response headers
            log.debug("[HEADERS] Response Headers: Content-Type={}, Content-Length={}", 
                    response.getHeaders().getFirst("Content-Type"),
                    response.getHeaders().getFirst("Content-Length"));
            
            // Read and log response body
            String responseBody = readResponseBody(response);
            if (!responseBody.isEmpty()) {
                if (statusCode >= 200 && statusCode < 400) {
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
            
        } catch (Exception e) {
            log.warn("[WARN] Failed to log TMForum response: {}", e.getMessage());
        }
    }

    private String readResponseBody(ClientHttpResponse response) {
        try {
            return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[WARN] Failed to read TMForum response body: {}", e.getMessage());
            return "";
        }
    }

    private static class BufferedClientHttpResponse implements ClientHttpResponse {
        private final ClientHttpResponse response;
        private byte[] body;

        public BufferedClientHttpResponse(ClientHttpResponse response) {
            this.response = response;
        }

        @Override
        public InputStream getBody() throws IOException {
            if (body == null) {
                body = StreamUtils.copyToByteArray(response.getBody());
            }
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            response.close();
        }

        // Delegate all other methods to the original response
        @Override
        public int getRawStatusCode() throws IOException {
            return response.getRawStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return response.getStatusText();
        }

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return response.getHeaders();
        }

        @Override
        public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
            return response.getStatusCode();
        }
    }
} 