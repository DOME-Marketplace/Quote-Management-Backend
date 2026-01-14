package com.dome.quotemanagement.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

@Configuration
public class AppConfig {
    
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    
    // Service-specific base URLs (NEW - each service on different port)
    @Value("${tmforum.api.quote.base-url}")
    private String quoteServiceBaseUrl;
    
    @Value("${tmforum.api.product.base-url}")
    private String productServiceBaseUrl;
    
    @Value("${tmforum.api.party.base-url}")
    private String partyServiceBaseUrl;
    
    // Service-specific endpoints
    @Value("${tmforum.api.quote.endpoint:/quoteManagement/v4/quote}")
    private String tmforumQuoteEndpoint;
    
    @Value("${tmforum.api.quote.list-endpoint:/quoteManagement/v4/quote?limit=1000}")
    private String tmforumQuoteListEndpoint;

    @Value("${tmforum.api.product.offering-endpoint:/productCatalogManagement/v4/productOffering}")
    private String tmforumProductCatalogManagementEndpoint;
    
    @Value("${tmforum.api.party.organization-endpoint:/party/v4/organization}")
    private String tmforumOrganizationEndpoint;
    
    @Value("${notification.api.endpoint:/charging/api/orderManagement/notify}")
    private String notificationEndpoint;
    
    @Value("${did.identifier:did:elsi:VATSB-12345678J}")
    private String didIdentifier;
    
    @Value("${tmforum.api.bearer-token:}")
    private String tmforumBearerToken;
    
    // Public getters for service-specific base URLs
    public String getQuoteServiceBaseUrl() {
        return quoteServiceBaseUrl;
    }
    
    public String getProductServiceBaseUrl() {
        return productServiceBaseUrl;
    }
    
    public String getPartyServiceBaseUrl() {
        return partyServiceBaseUrl;
    }
    
    // Public getters for endpoints
    public String getTmforumQuoteEndpoint() {
        return tmforumQuoteEndpoint;
    }
    
    public String getTmforumQuoteListEndpoint() {
        return tmforumQuoteListEndpoint;
    }
    
    public String getNotificationEndpoint() {
        return notificationEndpoint;
    }
    
    public String getDidIdentifier() {
        return didIdentifier;
    }
    
    public String getTmforumProductCatalogManagementEndpoint() {
        return tmforumProductCatalogManagementEndpoint;
    }
    
    public String getTmforumOrganizationEndpoint() {
        return tmforumOrganizationEndpoint;
    }
    
    // Helper methods to build complete URLs
    public String getQuoteServiceUrl() {
        return quoteServiceBaseUrl.trim() + tmforumQuoteEndpoint;
    }
    
    public String getQuoteServiceListUrl() {
        return quoteServiceBaseUrl.trim() + tmforumQuoteListEndpoint;
    }
    
    public String getProductServiceUrl() {
        return productServiceBaseUrl.trim() + tmforumProductCatalogManagementEndpoint;
    }
    
    public String getPartyServiceUrl() {
        return partyServiceBaseUrl.trim() + tmforumOrganizationEndpoint;
    }
    
    @Autowired
    private RestTemplateLoggingInterceptor restTemplateLoggingInterceptor;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Log bearer token configuration status
        if (tmforumBearerToken != null && !tmforumBearerToken.trim().isEmpty()) {
            log.info("TMForum Bearer Token configured (length: {} chars)", tmforumBearerToken.length());
        } else {
            log.warn("TMForum Bearer Token is NOT configured!");
        }
        
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
        
        try {
            // Configure Apache HttpClient5 with SSL trust all for development
            //TODO: Change the certificate for PROD
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                    .build();
            
            // Create connection manager with pooling
            PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(
                        SSLConnectionSocketFactoryBuilder.create()
                                .setSslContext(sslContext)
                                .setHostnameVerifier((hostname, session) -> true)
                                .build())
                    .setMaxConnTotal(100) // Maximum total connections
                    .setMaxConnPerRoute(20) // Maximum connections per route
                    .build();
            
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .evictIdleConnections(TimeValue.ofSeconds(30)) // Evict idle connections
                    .evictExpiredConnections() // Evict expired connections
                    .build();
            
            // Use Apache HttpClient5 instead of default SimpleClientHttpRequestFactory
            HttpComponentsClientHttpRequestFactory requestFactory = 
                new HttpComponentsClientHttpRequestFactory(httpClient);
            
            // Set timeouts
            requestFactory.setConnectTimeout(30000); // 30 seconds
            requestFactory.setConnectionRequestTimeout(30000); // 30 seconds
            
            restTemplate.setRequestFactory(requestFactory);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure SSL context", e);
        }
        
        // Add interceptor to set default headers for all requests
        // IMPORTANT: Order matters! Headers must be set BEFORE logging
        restTemplate.setInterceptors(Arrays.asList(
            // Add the default headers interceptor FIRST
            (request, body, execution) -> {
                HttpHeaders headers = request.getHeaders();
                
                // Always set Accept header to application/json
                if (!headers.containsKey(HttpHeaders.ACCEPT)) {
                    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                }
                
                // For requests with body (POST, PUT, PATCH), set Content-Type to application/json
                if (body != null && body.length > 0) {
                    if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
                        headers.setContentType(MediaType.APPLICATION_JSON);
                    }
                }
                
                // Add Authorization header if Bearer token is configured
                if (tmforumBearerToken != null && !tmforumBearerToken.trim().isEmpty()) {
                    if (!headers.containsKey(HttpHeaders.AUTHORIZATION)) {
                        headers.setBearerAuth(tmforumBearerToken.trim());
                        log.debug("Added Authorization Bearer token to request");
                    }
                } else {
                    log.debug("No Bearer token configured, skipping Authorization header");
                }
                
                return execution.execute(request, body);
            },
            // Add our logging interceptor LAST (so it logs the final headers)
            restTemplateLoggingInterceptor
        ));
        
        return restTemplate;
    }
} 