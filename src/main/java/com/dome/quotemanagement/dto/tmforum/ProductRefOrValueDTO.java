package com.dome.quotemanagement.dto.tmforum;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ProductRefOrValueDTO {
    @JsonProperty("@type")
    private String type;
    
    @JsonProperty("@baseType")
    private String baseType;
    
    @JsonProperty("@schemaLocation")
    private String schemaLocation;
    
    @JsonProperty("@referredType")
    private String referredType;
    
    private String id;
    private String href;
    private String name;
    private String description;
    private Boolean isBundle;
    private Boolean isCustomerVisible;
    private String orderDate;
    private String productSerialNumber;
    private String startDate;
    private String status;
    private String terminationDate;
    private List<ProductCharacteristicDTO> productCharacteristic;
} 