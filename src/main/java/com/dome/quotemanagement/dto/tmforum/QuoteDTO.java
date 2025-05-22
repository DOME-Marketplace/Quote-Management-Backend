package com.dome.quotemanagement.dto.tmforum;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuoteDTO {
    @JsonProperty("@type")
    private String type;
    
    @JsonProperty("@baseType")
    private String baseType;
    
    @JsonProperty("@schemaLocation")
    private String schemaLocation;
    
    private String id;
    private String href;
    private String description;
    private String category;
    private LocalDateTime effectiveQuoteCompletionDate;
    private LocalDateTime expectedFulfillmentStartDate;
    private LocalDateTime expectedQuoteCompletionDate;
    private String externalId;
    private Boolean instantSyncQuote;
    private LocalDateTime quoteDate;
    private String quoteLevel;
    private String state;
    private String version;
    private List<QuoteItemDTO> quoteItem;
    private List<RelatedPartyDTO> relatedParty;
    private List<NoteDTO> note;
    private List<AttachmentDTO> attachment;
} 