package com.dome.quotemanagement.dto.tmforum;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSpecificationCreateRequestDTO {
    private String name;
    private String description;
    private String version;
    private String lifecycleStatus;
    private List<DocumentAttachmentDTO> attachment;
}
