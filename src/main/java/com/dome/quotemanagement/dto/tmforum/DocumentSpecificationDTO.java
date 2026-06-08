package com.dome.quotemanagement.dto.tmforum;

import lombok.Data;

import java.util.List;

@Data
public class DocumentSpecificationDTO {
    private String id;
    private String href;
    private String name;
    private String description;
    private String version;
    private String lifecycleStatus;
    private String lastUpdate;
    private List<DocumentAttachmentDTO> attachment;
}
