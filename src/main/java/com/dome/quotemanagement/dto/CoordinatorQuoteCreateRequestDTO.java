package com.dome.quotemanagement.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class CoordinatorQuoteCreateRequestDTO {
    @NotBlank(message = "Customer message is required for coordinator quotes")
    private String customerMessage; // Required - used as title/description for the tendering process
    
    @NotBlank(message = "Customer ID reference is required")
    private String customerIdRef; // Required - the customer initiating the tendering process
}
