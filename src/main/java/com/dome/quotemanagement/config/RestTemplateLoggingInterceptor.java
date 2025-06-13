package com.dome.quotemanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

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
            
            // Log the response from TMForum
            logTMForumResponse(request, response, System.currentTimeMillis() - startTime);
            
            return response;
            
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
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("Failed to read TMForum response body: {}", e.getMessage());
            return "";
        }
    }
} 