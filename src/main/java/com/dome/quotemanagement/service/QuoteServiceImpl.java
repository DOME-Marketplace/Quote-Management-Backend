package com.dome.quotemanagement.service;

import com.dome.quotemanagement.dto.tmforum.AttachmentRefOrValueDTO;
import com.dome.quotemanagement.dto.tmforum.QuoteDTO;
import com.dome.quotemanagement.dto.tmforum.QuoteItemDTO;
import com.dome.quotemanagement.dto.tmforum.NoteDTO;
import com.dome.quotemanagement.dto.NotificationRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteServiceImpl implements QuoteService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    
    @Value("${tmforum.api.base-url}")
    private String tmforumBaseUrl;
    
    @Override
    public List<QuoteDTO> findAllQuotes() {
        String url = tmforumBaseUrl.trim() + "/quoteManagement/v4/quote?limit=100";
        log.debug("Calling external TMForum API to get all quotes: {}", url);
        try {
            QuoteDTO[] quotes = restTemplate.getForObject(url, QuoteDTO[].class);
            return Arrays.asList(quotes != null ? quotes : new QuoteDTO[0]);
        } catch (Exception e) {
            log.error("Error calling TMForum API: {}", e.getMessage());
            return Arrays.asList();
        }
    }
    
    @Override
    public List<QuoteDTO> findQuotesByUser(String customerId) {
        String url = tmforumBaseUrl.trim() + "/quoteManagement/v4/quote?limit=100";
        log.debug("Calling external TMForum API to get quotes for customer {}: {}", customerId, url);
        try {
            QuoteDTO[] quotes = restTemplate.getForObject(url, QuoteDTO[].class);
            List<QuoteDTO> allQuotes = Arrays.asList(quotes != null ? quotes : new QuoteDTO[0]);
            
            // Filter quotes based on customerId matching RelatedParty id
            return allQuotes.stream()
                    .filter(quote -> isQuoteRelatedToCustomer(quote, customerId))
                    .collect(java.util.stream.Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error calling TMForum API: {}", e.getMessage());
            return Arrays.asList();
        }
    }
    
    @Override
    public Optional<QuoteDTO> findById(String id) {
        String url = tmforumBaseUrl.trim() + "/quoteManagement/v4/quote/" + id;
        log.debug("Calling external TMForum API: {}", url);
        try {
            QuoteDTO quote = restTemplate.getForObject(url, QuoteDTO.class);
            return Optional.ofNullable(quote);
        } catch (Exception e) {
            log.error("Error calling TMForum API: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public QuoteDTO create(String customerMessage, String customerIdRef, String providerIdRef, String productOfferingId) {
        String url = tmforumBaseUrl.trim() + "/quoteManagement/v4/quote";
        log.debug("Calling external TMForum API: {}", url);
        log.debug("Create quote parameters - customerMessage: '{}', customerIdRef: '{}', providerIdRef: '{}', productOfferingId: '{}'", 
                  customerMessage, customerIdRef, providerIdRef, productOfferingId);
        
        try {
            // Build a minimal JSON payload that conforms to TMForum standards
            String jsonPayload = buildCreateQuoteJson(customerMessage, customerIdRef, providerIdRef, productOfferingId);
            
            // Set proper headers for JSON
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
            
            log.info("Sending JSON payload to TMForum API: {}", jsonPayload);
            
            QuoteDTO response = restTemplate.postForObject(url, request, QuoteDTO.class);
            log.info("Received response from TMForum API: {}", response);

            // Send notification after successful quote creation
            if (response != null && response.getId() != null) {
                NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .seller(providerIdRef)
                    .customer(customerIdRef)
                    .message("New quote created with ID: " + response.getId() + 
                            (customerMessage != null ? "\nMessage: " + customerMessage : ""))
                    .build();
                
                notificationService.sendNotification(notification);
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("Error calling TMForum API: {}", e.getMessage());
            throw e; // Re-throw the original exception to preserve HTTP status codes
        }
    }
    
    @Override
    public Optional<QuoteDTO> updateQuoteStatus(String quoteId, String statusValue) {
        log.debug("Updating quote status - quoteId: '{}', statusValue: '{}'", quoteId, statusValue);
        
        try {
            // First, get the current quote
            Optional<QuoteDTO> currentQuoteOpt = findById(quoteId);
            if (currentQuoteOpt.isEmpty()) {
                log.warn("Quote not found with id: {}", quoteId);
                return Optional.empty();
            }
            
            QuoteDTO currentQuote = currentQuoteOpt.get();
            
            // Create a minimal update payload with just the status change at quoteItem level
            String jsonPayload = buildStatusUpdateJson(statusValue, currentQuote);
            
            String url = tmforumBaseUrl.trim() + "/quoteManagement/v4/quote/" + quoteId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
            
            log.info("Sending status update to URL: {}", url);
            log.info("Request headers: {}", headers);
            log.info("JSON payload: {}", jsonPayload);
            
            QuoteDTO updatedQuote = restTemplate.exchange(
                url, 
                HttpMethod.PATCH, 
                request, 
                QuoteDTO.class
            ).getBody();
            
            log.info("Received updated quote from TMForum API: {}", updatedQuote);
            return Optional.ofNullable(updatedQuote);
            
        } catch (Exception e) {
            log.error("Error updating quote status: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<QuoteDTO> updateQuoteNote(String quoteId, String userId, String messageContent) {
        log.debug("Updating quote note - quoteId: '{}', userId: '{}', messageContent: '{}'", quoteId, userId, messageContent);
        
        try {
            // First, get the current quote
            Optional<QuoteDTO> currentQuoteOpt = findById(quoteId);
            if (currentQuoteOpt.isEmpty()) {
                log.warn("Quote not found with id: {}", quoteId);
                return Optional.empty();
            }
            
            QuoteDTO currentQuote = currentQuoteOpt.get();
            
            // Create a minimal update payload with the new note appended to existing ones
            String jsonPayload = buildNoteUpdateJson(messageContent, userId, currentQuote);
            
            String url = tmforumBaseUrl.trim() + "/quoteManagement/v4/quote/" + quoteId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
            
            log.info("Sending note update to URL: {}", url);
            log.info("Request headers: {}", headers);
            log.info("JSON payload: {}", jsonPayload);
            
            QuoteDTO updatedQuote = restTemplate.exchange(
                url, 
                HttpMethod.PATCH, 
                request, 
                QuoteDTO.class
            ).getBody();
            
            log.info("Received updated quote from TMForum API: {}", updatedQuote);
            return Optional.ofNullable(updatedQuote);
            
        } catch (Exception e) {
            log.error("Error updating quote note: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<QuoteDTO> updateQuoteAttachment(String quoteId, MultipartFile file, String description) {
        log.debug("Updating quote attachment - quoteId: '{}', filename: '{}', description: '{}'", 
                  quoteId, file.getOriginalFilename(), description);

        try {
            // Validate file
            if (file.isEmpty()) {
                throw new IllegalArgumentException("File is empty");
            }
            
            // Validate file type (only PDF allowed)
            String contentType = file.getContentType();
            if (!"application/pdf".equals(contentType)) {
                throw new IllegalArgumentException("Only PDF files are allowed. Received: " + contentType);
            }
            
            // Validate file size (limit to 10MB)
            long maxSize = 10 * 1024 * 1024; // 10MB
            if (file.getSize() > maxSize) {
                throw new IllegalArgumentException("File size exceeds maximum allowed size of 10MB");
            }
            
            // First, get the current quote
            Optional<QuoteDTO> currentQuoteOpt = findById(quoteId);
            if (currentQuoteOpt.isEmpty()) {
                log.warn("Quote not found with id: {}", quoteId);
                return Optional.empty();
            }
            
            QuoteDTO currentQuote = currentQuoteOpt.get();
            
            // Convert file to AttachmentRefOrValueDTO
            AttachmentRefOrValueDTO attachment = createAttachmentFromFile(file, description);
            
            // Create a minimal update payload with the new attachment
            String jsonPayload = buildAttachmentUpdateJson(attachment, currentQuote);
            
            String url = tmforumBaseUrl.trim() + "/quoteManagement/v4/quote/" + quoteId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
            
            log.info("Sending attachment update to URL: {}", url);
            log.info("Request headers: {}", headers);
            log.info("JSON payload: {}", jsonPayload);
            
            QuoteDTO updatedQuote = restTemplate.exchange(
                url, 
                HttpMethod.PATCH, 
                request, 
                QuoteDTO.class
            ).getBody();
            
            log.info("Received updated quote from TMForum API: {}", updatedQuote);

            // Send notification to customer about the new document
            if (updatedQuote != null) {
                // Find customer ID from relatedParty
                String customerId = updatedQuote.getRelatedParty().stream()
                    .filter(party -> "customer".equals(party.getRole()))
                    .findFirst()
                    .map(party -> party.getId())
                    .orElse(null);

                // Find provider ID from relatedParty
                String providerId = updatedQuote.getRelatedParty().stream()
                    .filter(party -> "seller".equals(party.getRole()))
                    .findFirst()
                    .map(party -> party.getId())
                    .orElse(null);

                if (customerId != null && providerId != null) {
                    String message = String.format(
                        "A new document has been uploaded to your quote (ID: %s):\n" +
                        "Document: %s\n" +
                        "Description: %s",
                        quoteId,
                        file.getOriginalFilename(),
                        description != null && !description.isEmpty() ? description : "No description provided"
                    );

                    NotificationRequestDTO notification = NotificationRequestDTO.builder()
                        .seller(providerId)
                        .customer(customerId)
                        .message(message)
                        .build();

                    notificationService.sendNotification(notification);
                }
            }
            
            return Optional.ofNullable(updatedQuote);
            
        } catch (IllegalArgumentException e) {
            log.error("Validation error for file upload: {}", e.getMessage());
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            log.error("Error updating quote attachment: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<QuoteDTO> updateQuoteDate(String quoteId, String date) {
        log.debug("Updating quote date - quoteId: '{}', date: '{}'", quoteId, date);
        
        try {
            // First, get the current quote
            Optional<QuoteDTO> currentQuoteOpt = findById(quoteId);
            if (currentQuoteOpt.isEmpty()) {
                log.warn("Quote not found with id: {}", quoteId);
                return Optional.empty();
            }
            
            // Parse the user-friendly DD-MM-YYYY format and convert to ISO 8601
            String isoDate = convertDateToISO8601(date);
            
            // Create a minimal update payload with the converted date
            String jsonPayload = buildDateUpdateJson(isoDate);
            
            String url = tmforumBaseUrl.trim() + "/quoteManagement/v4/quote/" + quoteId;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
            
            log.info("Sending date update to URL: {}", url);
            log.info("Request headers: {}", headers);
            log.info("JSON payload: {}", jsonPayload);
            
            QuoteDTO updatedQuote = restTemplate.exchange(
                url, 
                HttpMethod.PATCH, 
                request, 
                QuoteDTO.class
            ).getBody();
            
            log.info("Received updated quote from TMForum API: {}", updatedQuote);
            return Optional.ofNullable(updatedQuote);
            
        } catch (Exception e) {
            log.error("Error updating quote date: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    @Override
    public void delete(String id) {
        String url = tmforumBaseUrl.trim() + "/quoteManagement/v4/quote/" + id;
        log.debug("Calling external TMForum API: {}", url);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            HttpEntity<String> requestEntity = new HttpEntity<>(headers);
            
            restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, Void.class);
        } catch (Exception e) {
            log.error("Error calling TMForum API: {}", e.getMessage());
            throw e; // Re-throw the original exception to preserve HTTP status codes
        }
    }
    
    /**
     * Build a JSON payload for status update
     */
    private String buildStatusUpdateJson(String statusValue, QuoteDTO currentQuote) {
        try {
            ObjectNode updateJson = objectMapper.createObjectNode();
            ArrayNode quoteItemArray = objectMapper.createArrayNode();
            
            // Check if current quote has quote items
            if (currentQuote.getQuoteItem() != null && !currentQuote.getQuoteItem().isEmpty()) {
                // Update all existing quote items with the new status
                for (QuoteItemDTO quoteItem : currentQuote.getQuoteItem()) {
                    ObjectNode quoteItemJson = objectMapper.createObjectNode();
                    quoteItemJson.put("state", statusValue);
                    
                    // Include the id if it exists to identify which quote item to update
                    if (quoteItem.getId() != null) {
                        quoteItemJson.put("id", quoteItem.getId());
                    }
                    
                    // Include other essential fields to maintain data integrity
                    if (quoteItem.getAction() != null) {
                        quoteItemJson.put("action", quoteItem.getAction());
                    }
                    if (quoteItem.getQuantity() != null) {
                        quoteItemJson.put("quantity", quoteItem.getQuantity());
                    }
                    
                    quoteItemArray.add(quoteItemJson);
                }
            } else {
                // If no quote items exist, create a minimal one with the new status
                ObjectNode quoteItemJson = objectMapper.createObjectNode();
                quoteItemJson.put("@type", "QuoteItem");
                quoteItemJson.put("state", statusValue);
                quoteItemJson.put("action", "add");
                quoteItemJson.put("quantity", 1);
                quoteItemArray.add(quoteItemJson);
            }
            
            updateJson.set("quoteItem", quoteItemArray);
            return objectMapper.writeValueAsString(updateJson);
        } catch (Exception e) {
            log.error("Error building status update JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to build status update JSON", e);
        }
    }

    /**
     * Build a JSON payload for note update
     */
    private String buildNoteUpdateJson(String messageContent, String userId, QuoteDTO currentQuote) {
        try {
            ObjectNode updateJson = objectMapper.createObjectNode();
            ArrayNode noteArray = objectMapper.createArrayNode();
            
            // First, preserve all existing notes to maintain conversation history
            if (currentQuote.getNote() != null && !currentQuote.getNote().isEmpty()) {
                for (NoteDTO existingNote : currentQuote.getNote()) {
                    ObjectNode existingNoteObject = objectMapper.createObjectNode();
                    existingNoteObject.put("@type", "Note");
                    if (existingNote.getText() != null) {
                        existingNoteObject.put("text", existingNote.getText());
                    }
                    if (existingNote.getDate() != null) {
                        // Convert LocalDateTime to Instant format for TMForum API compatibility
                        String dateString = existingNote.getDate().atZone(java.time.ZoneOffset.UTC).toInstant().toString();
                        existingNoteObject.put("date", dateString);
                    }
                    if (existingNote.getAuthor() != null) {
                        existingNoteObject.put("author", existingNote.getAuthor());
                    }
                    if (existingNote.getId() != null) {
                        existingNoteObject.put("id", existingNote.getId());
                    }
                    noteArray.add(existingNoteObject);
                }
            }
            
            // Then, append the new note to the conversation
            ObjectNode newNoteObject = objectMapper.createObjectNode();
            newNoteObject.put("@type", "Note");
            newNoteObject.put("text", messageContent);
            newNoteObject.put("date", Instant.now().toString()); // Use Instant format for TMForum API compatibility
            newNoteObject.put("author", userId); // Use the provided userId instead of "System"
            
            noteArray.add(newNoteObject);
            updateJson.set("note", noteArray);
            
            return objectMapper.writeValueAsString(updateJson);
        } catch (Exception e) {
            log.error("Error building note update JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to build note update JSON", e);
        }
    }

    /**
     * Build a JSON payload for attachment update
     */
    private String buildAttachmentUpdateJson(AttachmentRefOrValueDTO attachment, QuoteDTO currentQuote) {
        try {
            ObjectNode updateJson = objectMapper.createObjectNode();
            ArrayNode quoteItemArray = objectMapper.createArrayNode();
            
            // Add attachments to quoteItem, not to quote directly (per TMForum spec)
            if (currentQuote.getQuoteItem() != null && !currentQuote.getQuoteItem().isEmpty()) {
                // Update the first quote item with the new attachment
                QuoteItemDTO firstQuoteItem = currentQuote.getQuoteItem().get(0);
                
                ObjectNode quoteItemJson = objectMapper.createObjectNode();
                quoteItemJson.put("@type", "QuoteItem");
                
                // Preserve existing quote item properties
                if (firstQuoteItem.getId() != null) {
                    quoteItemJson.put("id", firstQuoteItem.getId());
                }
                if (firstQuoteItem.getAction() != null) {
                    quoteItemJson.put("action", firstQuoteItem.getAction());
                }
                if (firstQuoteItem.getQuantity() != null) {
                    quoteItemJson.put("quantity", firstQuoteItem.getQuantity());
                }
                if (firstQuoteItem.getState() != null) {
                    quoteItemJson.put("state", firstQuoteItem.getState());
                }
                
                // Create attachment array for this quote item
                ArrayNode attachmentArray = objectMapper.createArrayNode();
                
                // Preserve existing attachments if any
                if (firstQuoteItem.getAttachment() != null && !firstQuoteItem.getAttachment().isEmpty()) {
                    for (AttachmentRefOrValueDTO existingAttachment : firstQuoteItem.getAttachment()) {
                        ObjectNode existingAttachmentObject = objectMapper.createObjectNode();
                        existingAttachmentObject.put("@type", "AttachmentRefOrValue");
                        
                        if (existingAttachment.getName() != null) {
                            existingAttachmentObject.put("name", existingAttachment.getName());
                        }
                        if (existingAttachment.getDescription() != null) {
                            existingAttachmentObject.put("description", existingAttachment.getDescription());
                        }
                        if (existingAttachment.getContent() != null) {
                            existingAttachmentObject.put("content", existingAttachment.getContent());
                        }
                        if (existingAttachment.getMimeType() != null) {
                            existingAttachmentObject.put("mimeType", existingAttachment.getMimeType());
                        }
                        if (existingAttachment.getSize() != null) {
                            ObjectNode sizeObject = objectMapper.createObjectNode();
                            sizeObject.put("amount", existingAttachment.getSize().getAmount());
                            sizeObject.put("units", existingAttachment.getSize().getUnits());
                            existingAttachmentObject.set("size", sizeObject);
                        }
                        if (existingAttachment.getId() != null) {
                            existingAttachmentObject.put("id", existingAttachment.getId());
                        }
                        
                        attachmentArray.add(existingAttachmentObject);
                    }
                }
                
                // Add the new attachment
                ObjectNode newAttachmentObject = objectMapper.createObjectNode();
                newAttachmentObject.put("@type", "AttachmentRefOrValue");
                
                if (attachment.getContent() != null) {
                    newAttachmentObject.put("content", attachment.getContent());
                }
                if (attachment.getName() != null) {
                    newAttachmentObject.put("name", attachment.getName());
                }
                if (attachment.getDescription() != null) {
                    newAttachmentObject.put("description", attachment.getDescription());
                }
                if (attachment.getMimeType() != null) {
                    newAttachmentObject.put("mimeType", attachment.getMimeType());
                }
                if (attachment.getSize() != null) {
                    ObjectNode sizeObject = objectMapper.createObjectNode();
                    sizeObject.put("amount", attachment.getSize().getAmount());
                    sizeObject.put("units", attachment.getSize().getUnits());
                    newAttachmentObject.set("size", sizeObject);
                }
                
                attachmentArray.add(newAttachmentObject);
                quoteItemJson.set("attachment", attachmentArray);
                quoteItemArray.add(quoteItemJson);
            } else {
                // If no quote items exist, create a minimal one with the attachment
                ObjectNode quoteItemJson = objectMapper.createObjectNode();
                quoteItemJson.put("@type", "QuoteItem");
                quoteItemJson.put("action", "add");
                quoteItemJson.put("quantity", 1);
                quoteItemJson.put("state", "inProgress");
                
                ArrayNode attachmentArray = objectMapper.createArrayNode();
                ObjectNode newAttachmentObject = objectMapper.createObjectNode();
                newAttachmentObject.put("@type", "AttachmentRefOrValue");
                
                if (attachment.getContent() != null) {
                    newAttachmentObject.put("content", attachment.getContent());
                }
                if (attachment.getName() != null) {
                    newAttachmentObject.put("name", attachment.getName());
                }
                if (attachment.getDescription() != null) {
                    newAttachmentObject.put("description", attachment.getDescription());
                }
                if (attachment.getMimeType() != null) {
                    newAttachmentObject.put("mimeType", attachment.getMimeType());
                }
                if (attachment.getSize() != null) {
                    ObjectNode sizeObject = objectMapper.createObjectNode();
                    sizeObject.put("amount", attachment.getSize().getAmount());
                    sizeObject.put("units", attachment.getSize().getUnits());
                    newAttachmentObject.set("size", sizeObject);
                }
                
                attachmentArray.add(newAttachmentObject);
                quoteItemJson.set("attachment", attachmentArray);
                quoteItemArray.add(quoteItemJson);
            }
            
            updateJson.set("quoteItem", quoteItemArray);
            
            String jsonPayload = objectMapper.writeValueAsString(updateJson);
            log.info("TMForum-compliant quoteItem attachment JSON payload: {}", jsonPayload);
            
            return jsonPayload;
        } catch (Exception e) {
            log.error("Error building attachment update JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to build attachment update JSON", e);
        }
    }

    /**
     * Build a JSON payload for date update
     */
    private String buildDateUpdateJson(String date) {
        try {
            ObjectNode updateJson = objectMapper.createObjectNode();
            updateJson.put("requestedQuoteCompletionDate", date);
            return objectMapper.writeValueAsString(updateJson);
        } catch (Exception e) {
            log.error("Error building date update JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to build date update JSON", e);
        }
    }
    
    /**
     * Build a minimal JSON payload that conforms to TMForum Quote creation standards
     */
    private String buildCreateQuoteJson(String customerMessage, String customerIdRef, String providerIdRef, String productOfferingId) {
        try {
            // Create the root JSON object
            ObjectNode quoteJson = objectMapper.createObjectNode();
            
            // Add description if provided
            if (customerMessage != null && !customerMessage.trim().isEmpty()) {
                quoteJson.put("description", customerMessage);
            }
            
            // Add requestedQuoteCompletionDate (current date + 30 days)
            Instant requestedCompletionDate = Instant.now().plusSeconds(30 * 24 * 60 * 60); // 30 days in seconds
            quoteJson.put("requestedQuoteCompletionDate", requestedCompletionDate.toString());
            
            // Add relatedParty array
            ArrayNode relatedPartyArray = objectMapper.createArrayNode();
            
            // Add customer related party if provided
            if (customerIdRef != null && !customerIdRef.trim().isEmpty()) {
                ObjectNode customerParty = objectMapper.createObjectNode();
                customerParty.put("id", customerIdRef);
                customerParty.put("href", customerIdRef);
                customerParty.put("role", "customer");
                customerParty.put("@referredType", "individual");
                relatedPartyArray.add(customerParty);
            }
            
            // Add provider related party if provided
            if (providerIdRef != null && !providerIdRef.trim().isEmpty()) {
                ObjectNode providerParty = objectMapper.createObjectNode();
                providerParty.put("id", providerIdRef);
                providerParty.put("href", providerIdRef);
                providerParty.put("role", "seller");
                providerParty.put("@referredType", "organization");
                relatedPartyArray.add(providerParty);
            }
            
            quoteJson.set("relatedParty", relatedPartyArray);
            
            // Add at least one quoteItem as required by the API
            ArrayNode quoteItemArray = objectMapper.createArrayNode();
            ObjectNode quoteItem = objectMapper.createObjectNode();
            quoteItem.put("@type", "QuoteItem");
            quoteItem.put("action", "add");
            quoteItem.put("state", "inProgress");
            quoteItem.put("quantity", 1);
            quoteItem.set("note", objectMapper.createObjectNode()); // Empty note object
            
            // Add productOffering reference if productOfferingId is provided
            if (productOfferingId != null && !productOfferingId.trim().isEmpty()) {
                ObjectNode productOffering = objectMapper.createObjectNode();
                productOffering.put("id", productOfferingId);
                productOffering.put("@type", "ProductOfferingRef");
                quoteItem.set("productOffering", productOffering);
            }
            
            quoteItemArray.add(quoteItem);
            quoteJson.set("quoteItem", quoteItemArray);
            
            // Convert to JSON string
            return objectMapper.writeValueAsString(quoteJson);
            
        } catch (Exception e) {
            log.error("Error building quote JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to build quote JSON", e);
        }
    }

    /**
     * Convert user-friendly date format to ISO 8601 format
     */
    private String convertDateToISO8601(String date) {
        try {
            // Parse the user-friendly DD-MM-YYYY format
            java.time.format.DateTimeFormatter inputFormatter = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");
            java.time.LocalDate localDate = java.time.LocalDate.parse(date, inputFormatter);
            
            // Convert to LocalDateTime with end of day time (23:59:59) as completion date
            java.time.LocalDateTime localDateTime = localDate.atTime(23, 59, 59);
            
            // Convert to ISO 8601 format (UTC timezone)
            java.time.Instant instant = localDateTime.atZone(ZoneOffset.UTC).toInstant();
            return instant.toString();
            
        } catch (java.time.format.DateTimeParseException e) {
            log.error("Invalid date format. Expected DD-MM-YYYY, got: {}", date);
            throw new IllegalArgumentException("Invalid date format. Expected DD-MM-YYYY (e.g., 31-12-2024), got: " + date);
        } catch (Exception e) {
            log.error("Error converting date: {}", e.getMessage());
            throw new RuntimeException("Failed to convert date: " + e.getMessage(), e);
        }
    }
        
    /**
     * Check if a quote is related to the specified customer by looking at RelatedParty objects
     */
    private boolean isQuoteRelatedToCustomer(QuoteDTO quote, String customerId) {
        if (quote.getRelatedParty() == null || quote.getRelatedParty().isEmpty()) {
            return false;
        }
        
        return quote.getRelatedParty().stream()
                .anyMatch(relatedParty -> customerId.equals(relatedParty.getId()));
    }
    
    /**
     * Create AttachmentRefOrValueDTO from uploaded file
     */
    private AttachmentRefOrValueDTO createAttachmentFromFile(MultipartFile file, String description) {
        try {
            // Encode file content as base64
            byte[] fileBytes = file.getBytes();
            String base64Content = java.util.Base64.getEncoder().encodeToString(fileBytes);
            
            // Create attachment DTO with embedded base64 content
            AttachmentRefOrValueDTO attachment = new AttachmentRefOrValueDTO();
            attachment.setType("AttachmentRefOrValue");
            attachment.setName(file.getOriginalFilename());
            attachment.setMimeType(file.getContentType());
            
            // Create proper Quantity object for size (TMForum spec compliance)
            com.dome.quotemanagement.dto.tmforum.QuantityDTO sizeQuantity = 
                new com.dome.quotemanagement.dto.tmforum.QuantityDTO(
                    (float) file.getSize(), 
                    "bytes"
                );
            attachment.setSize(sizeQuantity);
            
            attachment.setContent(base64Content); // Use content property with base64 encoded file
            
            if (description != null && !description.trim().isEmpty()) {
                attachment.setDescription(description);
            } else {
                attachment.setDescription("PDF document: " + file.getOriginalFilename());
            }
            
            log.info("Created attachment with base64 content - file: {}, size: {} bytes", 
                    file.getOriginalFilename(), file.getSize());
            
            return attachment;
            
        } catch (Exception e) {
            log.error("Error creating attachment from file: {}", e.getMessage());
            throw new RuntimeException("Failed to process uploaded file: " + e.getMessage(), e);
        }
    }
}





/*
 *  
State Definition
InProgress  -   The In Progress state is when the quote is currently in the hands
                of the SP sales team to build it regarding customer requirements. 
                The quote is under construction and needs more information. 
                Everything should be updatable in this state
Pending     -   The Pending state is used when a quote needs to be validated from the SP
                perspective for tariff validation or to capture detailed information. 
                The Pending state could be only for some quote items.
Approved    -   The Approved state is where the quote has been internally approved and sent to the 
                customer. The quote is no longer updatable. 
Cancelled   -   The Cancelled state is when the quote process is stopped from a SP decision. 
                A cancelled quote has never been send to the customer.
Accepted    -   The Accepted state is used when the customer agreed to commit to the order and 
                signed the quote. 
Rejected    -   The Rejected state is used when the customer does not wish to progress with the 
                quotation. It could his final decision and no other quote will be initiated from 
                this quote or
 */