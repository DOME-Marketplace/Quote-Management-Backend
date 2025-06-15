package com.dome.quotemanagement.config;

import lombok.extern.slf4j.Slf4j;
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

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                                      ClientHttpRequestExecution execution) throws IOException {
        
        long startTime = System.currentTimeMillis();
        
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
            log.error("=== TMForum API ERROR === {} {} - Duration: {}ms - Error: {}", 
                    request.getMethod(), request.getURI(), duration, e.getMessage());
            throw e;
        }
    }

    private void logTMForumRequest(HttpRequest request, byte[] body) {
        String method = request.getMethod().toString();
        String url = request.getURI().toString();
        
        log.info("=== TMForum OUTGOING REQUEST === {} {}", method, url);
        
        // Log request headers (selective)
        log.debug("TMForum Request Headers: Content-Type={}, Accept={}", 
                request.getHeaders().getFirst("Content-Type"),
                request.getHeaders().getFirst("Accept"));
        
        // Log request body for operations that have a body
        if (body != null && body.length > 0) {
            String requestBody = new String(body, StandardCharsets.UTF_8);
            log.info("TMForum Request Payload: {}", requestBody);
        }
    }

    private void logTMForumResponse(HttpRequest request, ClientHttpResponse response, long duration) {
        try {
            String method = request.getMethod().toString();
            String url = request.getURI().toString();
            int statusCode = response.getStatusCode().value();
            
            log.info("=== TMForum INCOMING RESPONSE === {} {} - Status: {} - Duration: {}ms", 
                    method, url, statusCode, duration);
            
            // Log response headers
            log.debug("TMForum Response Headers: Content-Type={}, Content-Length={}", 
                    response.getHeaders().getFirst("Content-Type"),
                    response.getHeaders().getFirst("Content-Length"));
            
            // Read and log response body
            String responseBody = readResponseBody(response);
            if (!responseBody.isEmpty()) {
                if (statusCode >= 200 && statusCode < 400) {
                    // Success response
                    if (responseBody.length() < 5000) {
                        log.info("TMForum Response Payload: {}", responseBody);
                    } else {
                        log.info("TMForum Response Payload: [LARGE_RESPONSE_TRUNCATED] Size: {} characters", 
                                responseBody.length());
                        log.debug("Full TMForum Response: {}", responseBody); // Full response in DEBUG
                    }
                } else {
                    // Error response - always log fully
                    log.error("TMForum Error Response Payload: {}", responseBody);
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to log TMForum response: {}", e.getMessage());
        }
    }

    private String readResponseBody(ClientHttpResponse response) {
        try {
            return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to read TMForum response body: {}", e.getMessage());
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