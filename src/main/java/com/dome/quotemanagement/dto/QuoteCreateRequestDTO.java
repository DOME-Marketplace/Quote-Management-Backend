package com.dome.quotemanagement.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class QuoteCreateRequestDTO {
    private String buyerMessage; // Optional - can be null or empty
    
    @NotBlank(message = "Buyer ID reference is required")
    private String buyerIdRef;
    
    @NotBlank(message = "Provider ID reference is required")
    private String providerIdRef;
    
    private String productOfferingId; // Optional - can be null or empty
} 