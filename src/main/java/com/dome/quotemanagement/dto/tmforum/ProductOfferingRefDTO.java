package com.dome.quotemanagement.dto.tmforum;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductOfferingRefDTO {
    private String id;
    private String href;
    private String name;
    
    @JsonProperty("@baseType")
    private String baseType;
    
    @JsonProperty("@schemaLocation")
    private String schemaLocation;
    
    @JsonProperty("@type")
    private String type;
    
    @JsonProperty("@referredType")
    private String referredType;
    
    // Convenience constructor with just ID
    public ProductOfferingRefDTO(String id) {
        this.id = id;
        this.type = "ProductOfferingRef";
    }
} 