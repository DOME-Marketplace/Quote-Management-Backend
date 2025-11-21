package com.dome.quotemanagement.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class CoordinatorQuoteCreateRequestDTO {
    @NotBlank(message = "Buyer message is required for coordinator quotes")
    private String buyerMessage; // Required - used as title/description for the tendering process
    
    @NotBlank(message = "Buyer ID reference is required")
    private String buyerIdRef; // Required - the buyer initiating the tendering process
}
