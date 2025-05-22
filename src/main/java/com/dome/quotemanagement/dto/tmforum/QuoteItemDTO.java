package com.dome.quotemanagement.dto.tmforum;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


@Data
public class QuoteItemDTO {
    @JsonProperty("@type")
    private String type;
    
    @JsonProperty("@baseType")
    private String baseType;
    
    @JsonProperty("@schemaLocation")
    private String schemaLocation;
    
    private String id;
    private String href;
    private String action;
    private ProductRefOrValueDTO product;
    private Integer quantity;
    private String state;
} 