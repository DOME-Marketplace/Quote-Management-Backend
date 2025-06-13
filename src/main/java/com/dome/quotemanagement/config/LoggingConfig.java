package com.dome.quotemanagement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

@Configuration
public class LoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter loggingFilter = new CommonsRequestLoggingFilter();
        
        // Include query string
        loggingFilter.setIncludeQueryString(true);
        
        // Include request payload
        loggingFilter.setIncludePayload(true);
        loggingFilter.setMaxPayloadLength(5000);
        
        // Include headers (selective)
        loggingFilter.setIncludeHeaders(false); // We'll log headers selectively in our custom filter
        
        // Include client info
        loggingFilter.setIncludeClientInfo(true);
        
        // Set message prefix
        loggingFilter.setBeforeMessagePrefix("=== INCOMING REQUEST === ");
        loggingFilter.setAfterMessagePrefix("=== REQUEST PROCESSED === ");
        
        return loggingFilter;
    }
} 