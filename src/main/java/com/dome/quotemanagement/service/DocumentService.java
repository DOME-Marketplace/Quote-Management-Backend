package com.dome.quotemanagement.service;

import com.dome.quotemanagement.dto.tmforum.DocumentSpecificationDTO;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

    /**
     * Uploads a file to the TMForum Document API and returns the created document specification.
     */
    DocumentSpecificationDTO createDocumentSpecification(MultipartFile file, String description);

    /**
     * Deletes a document specification from the TMForum Document API (including S3 storage).
     */
    void deleteDocumentSpecification(String documentId);
}
