package com.dome.quotemanagement.dto.tmforum;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    private ProductOfferingRefDTO productOffering;
    private Integer quantity;
    private String state;
    private List<AttachmentRefOrValueDTO> attachment;
} 