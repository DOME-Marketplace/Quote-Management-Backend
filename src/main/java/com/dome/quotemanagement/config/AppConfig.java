package com.dome.quotemanagement.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import javax.net.ssl.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

@Configuration
public class AppConfig {
    
    @Autowired
    private RestTemplateLoggingInterceptor restTemplateLoggingInterceptor;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
        
        try {
            // Configure Apache HttpClient5 with SSL trust all for development
            //TODO: Change the certificate for PROD
/*             TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }  
            }; */

            
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                    .build();
            
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setConnectionManager(
                        PoolingHttpClientConnectionManagerBuilder.create()
                                .setSSLSocketFactory(
                                    SSLConnectionSocketFactoryBuilder.create()
                                            .setSslContext(sslContext)
                                            .setHostnameVerifier((hostname, session) -> true)
                                            .build())
                                .build())
                    .build();
            
            // Use Apache HttpClient5 instead of default SimpleClientHttpRequestFactory
            HttpComponentsClientHttpRequestFactory requestFactory = 
                new HttpComponentsClientHttpRequestFactory(httpClient);
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