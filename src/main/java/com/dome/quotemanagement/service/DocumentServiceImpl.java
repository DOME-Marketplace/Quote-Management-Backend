package com.dome.quotemanagement.service;

import com.dome.quotemanagement.config.AppConfig;
import com.dome.quotemanagement.dto.tmforum.DocumentAttachmentDTO;
import com.dome.quotemanagement.dto.tmforum.DocumentSpecificationCreateRequestDTO;
import com.dome.quotemanagement.dto.tmforum.DocumentSpecificationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final RestTemplate restTemplate;
    private final AppConfig appConfig;

    @Override
    public DocumentSpecificationDTO createDocumentSpecification(MultipartFile file, String description) {
        try {
            String url = appConfig.getTmforumDocumentApiBaseUrl().trim() + appConfig.getTmforumDocumentEndpoint();
            String fileName = file.getOriginalFilename();
            String base64Content = Base64.getEncoder().encodeToString(file.getBytes());

            DocumentSpecificationCreateRequestDTO requestBody = DocumentSpecificationCreateRequestDTO.builder()
                    .name(fileName)
                    .description(description != null && !description.trim().isEmpty()
                            ? description
                            : "PDF document: " + fileName)
                    .version("1.0")
                    .lifecycleStatus("active")
                    .attachment(List.of(
                            DocumentAttachmentDTO.builder()
                                    .name(fileName)
                                    .mimeType(file.getContentType())
                                    .content(base64Content)
                                    .build()
                    ))
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");

            HttpEntity<DocumentSpecificationCreateRequestDTO> request = new HttpEntity<>(requestBody, headers);

            log.info("Uploading document to TMForum Document API - url: {}, file: {}, size: {} bytes",
                    url, fileName, file.getSize());

            DocumentSpecificationDTO response = restTemplate.postForObject(url, request, DocumentSpecificationDTO.class);

            if (response == null || response.getId() == null || response.getId().isBlank()) {
                throw new RuntimeException("Document API did not return a valid document id");
            }

            log.info("Document uploaded successfully - documentId: {}, file: {}", response.getId(), fileName);
            return response;

        } catch (Exception e) {
            log.error("Failed to upload document to TMForum Document API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload document: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteDocumentSpecification(String documentId) {
        try {
            String url = appConfig.getTmforumDocumentApiBaseUrl().trim()
                    + appConfig.getTmforumDocumentEndpoint()
                    + "/" + documentId;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");

            HttpEntity<Void> request = new HttpEntity<>(headers);

            log.info("Deleting document from TMForum Document API - url: {}, documentId: {}", url, documentId);

            restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);

            log.info("Document deleted successfully - documentId: {}", documentId);

        } catch (Exception e) {
            log.error("Failed to delete document from TMForum Document API - documentId: {}: {}",
                    documentId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete document: " + e.getMessage(), e);
        }
    }
}
