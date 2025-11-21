package com.dome.quotemanagement.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class TenderingQuoteCreateRequestDTO {
    private String buyerMessage; // Optional - can be null or empty
    
    @NotBlank(message = "Buyer ID reference is required")
    private String buyerIdRef;
    
    @NotBlank(message = "Provider ID reference is required")
    private String providerIdRef;
    
    @NotNull(message = "External ID is required for tendering quotes")
    @NotBlank(message = "External ID cannot be empty")
    private String externalId; // Required - used to group related quotes in the same tendering process
}
