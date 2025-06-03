package com.dome.quotemanagement.dto.tmforum;

import lombok.Data;

@Data
public class QuantityDTO {
    private Float amount = 1.0f;
    private String units;
    
    public QuantityDTO() {
    }
    
    public QuantityDTO(Float amount, String units) {
        this.amount = amount;
        this.units = units;
    }
} 