package com.dome.quotemanagement.dto.tmforum;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AttachmentRefOrValueDTO {
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
    private String attachmentType;
    private String content;
    private String description;
    private String mimeType;
    private String name;
    private String url;
    private QuantityDTO size;
    private String validFor;
} 