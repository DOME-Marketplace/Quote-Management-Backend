package com.dome.quotemanagement.dto.tmforum;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAttachmentDTO {
    private String name;
    private String mimeType;
    private String content;
}
