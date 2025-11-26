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

import javax.net.ssl.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

@Configuration
public class AppConfig {
    
    @Value("${tmforum.api.quote-endpoint:/quote/v4/quote}")
    private String tmforumQuoteEndpoint;
    
    @Value("${tmforum.api.quote-list-endpoint:/quote/v4/quote?limit=1000}")
    private String tmforumQuoteListEndpoint;

    @Value("${tmforum.api.product-offering-endpoint:/productCatalogManagement/v4/productOffering}")
    private String tmforumProductCatalogManagementEndpoint;
    
    @Value("${tmforum.api.organization-endpoint:/party/v4/organization}")
    private String tmforumOrganizationEndpoint;
    
    @Value("${notification.api.endpoint:/charging/api/orderManagement/notify}")
    private String notificationEndpoint;
    
    @Value("${did.identifier:did:elsi:VATES-11111111P}")
    private String didIdentifier;
    
    // Public getters to maintain backward compatibility
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
    
    @Autowired
    private RestTemplateLoggingInterceptor restTemplateLoggingInterceptor;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
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
                    .setValidateAfterInactivity(TimeValue.ofSeconds(30)) // Validate connections after inactivity
                    .build();
            
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .setKeepAliveStrategy((response, context) -> TimeValue.ofSeconds(60)) // Keep-alive duration
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
        restTemplate.setInterceptors(Arrays.asList(
            // Add our logging interceptor first
            restTemplateLoggingInterceptor,
            // Then add the default headers interceptor
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
                
                return execution.execute(request, body);
            }
        ));
        
        return restTemplate;
    }
} 