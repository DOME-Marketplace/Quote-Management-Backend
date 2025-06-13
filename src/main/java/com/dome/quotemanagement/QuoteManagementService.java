package com.dome.quotemanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class QuoteManagementService {
    public static void main(String[] args) {
        SpringApplication.run(QuoteManagementService.class, args);
    }
} 