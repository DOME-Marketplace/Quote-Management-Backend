package com.dome.quotemanagement.service;

import com.dome.quotemanagement.config.AppConfig;
import com.dome.quotemanagement.dto.tmforum.QuoteDTO;
import com.dome.quotemanagement.dto.tmforum.QuoteItemDTO;
import com.dome.quotemanagement.dto.tmforum.NoteDTO;
import com.dome.quotemanagement.dto.NotificationRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteExpirationScheduler {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final AppConfig appConfig;

    @Value("${tmforum.api.base-url}")
    private String tmforumBaseUrl;

    @Scheduled(cron = "0 0 0 * * ?") // Run at midnight every day
    public void checkExpiredQuotes() {
        log.info("Starting scheduled check for expired quotes");
        try {
            // Get all quotes
            String url = tmforumBaseUrl.trim() + appConfig.getTmforumQuoteListEndpoint();
            QuoteDTO[] quotes = restTemplate.getForObject(url, QuoteDTO[].class);
            List<QuoteDTO> allQuotes = Arrays.asList(quotes != null ? quotes : new QuoteDTO[0]);

            // Check each quote
            for (QuoteDTO quote : allQuotes) {
                if (isQuoteExpired(quote)) {
                    handleExpiredQuote(quote);
                }
            }
        } catch (Exception e) {
            log.error("Error checking expired quotes: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 0 0 * * ?") // Run at midnight every day
    public void checkTenderQuotesStatus() {
        log.info("Starting scheduled check for tender quotes status updates");
        try {
            // Get all quotes
            String url = tmforumBaseUrl.trim() + appConfig.getTmforumQuoteListEndpoint();
            QuoteDTO[] quotes = restTemplate.getForObject(url, QuoteDTO[].class);
            List<QuoteDTO> allQuotes = Arrays.asList(quotes != null ? quotes : new QuoteDTO[0]);

            // Check each tender quote
            for (QuoteDTO quote : allQuotes) {
                if ("tender".equals(quote.getCategory())) {
                    checkAndUpdateTenderQuoteStatus(quote);
                }
            }
        } catch (Exception e) {
            log.error("Error checking tender quotes status: {}", e.getMessage(), e);
        }
    }

    private boolean isQuoteExpired(QuoteDTO quote) {
        if (quote.getRequestedQuoteCompletionDate() == null) {
            return false;
        }

        LocalDateTime completionDate = quote.getRequestedQuoteCompletionDate();
        LocalDateTime now = LocalDateTime.now();

        return now.isAfter(completionDate) && "inProgress".equals(quote.getState());
    }

    private void checkAndUpdateTenderQuoteStatus(QuoteDTO quote) {
        log.debug("Checking tender quote status for quote: {}", quote.getId());
        
        try {
            LocalDateTime now = LocalDateTime.now();
            String currentState = quote.getState();
            
            // Check if we need to update to "approved" (when expectedFulfillmentStartDate is passed)
            if ("inProgress".equals(currentState) && 
                quote.getExpectedFulfillmentStartDate() != null &&
                now.isAfter(quote.getExpectedFulfillmentStartDate())) {
                
                log.info("Updating tender quote {} from inProgress to approved - expectedFulfillmentStartDate passed", quote.getId());
                updateTenderQuoteStatus(quote, "approved", 
                    "Quote automatically approved - expected fulfillment start date has been reached.");
            }
            // Check if we need to update to "accepted" (when effectiveQuoteCompletionDate is passed)
            else if ("approved".equals(currentState) && 
                     quote.getEffectiveQuoteCompletionDate() != null &&
                     now.isAfter(quote.getEffectiveQuoteCompletionDate())) {
                
                log.info("Updating tender quote {} from approved to accepted - effectiveQuoteCompletionDate passed", quote.getId());
                updateTenderQuoteStatus(quote, "accepted", 
                    "Quote automatically accepted - effective quote completion date has been reached.");
            }
            
        } catch (Exception e) {
            log.error("Error checking tender quote status for quote {}: {}", quote.getId(), e.getMessage(), e);
        }
    }

    private void updateTenderQuoteStatus(QuoteDTO quote, String newStatus, String noteMessage) {
        try {
            String url = tmforumBaseUrl.trim() + appConfig.getTmforumQuoteEndpoint() + "/" + quote.getId();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");

            // Update quote status
            String jsonPayload = buildStatusUpdateJson(newStatus, quote);
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

            restTemplate.exchange(url, org.springframework.http.HttpMethod.PATCH, request, QuoteDTO.class);

            // Add note about status change
            String notePayload = buildNoteUpdateJson(noteMessage, "SYSTEM", quote);
            HttpEntity<String> noteRequest = new HttpEntity<>(notePayload, headers);
            restTemplate.exchange(url, org.springframework.http.HttpMethod.PATCH, noteRequest, QuoteDTO.class);

            // Send notifications
            sendTenderStatusChangeNotifications(quote, newStatus);

            log.info("Successfully updated tender quote {} to status: {}", quote.getId(), newStatus);
        } catch (Exception e) {
            log.error("Error updating tender quote {} status to {}: {}", quote.getId(), newStatus, e.getMessage(), e);
        }
    }

    private void sendTenderStatusChangeNotifications(QuoteDTO quote, String newStatus) {
        try {
            // Find customer and provider IDs
            String customerId = quote.getRelatedParty().stream()
                .filter(party -> "customer".equals(party.getRole()))
                .findFirst()
                .map(party -> party.getId())
                .orElse(null);

            String providerId = quote.getRelatedParty().stream()
                .filter(party -> "seller".equals(party.getRole()))
                .findFirst()
                .map(party -> party.getId())
                .orElse(null);

            if (customerId != null && providerId != null) {
                String message = getTenderStatusChangeMessage(quote, newStatus);

                NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .sender(providerId)
                    .recipient(customerId)
                    .subject("Tender Quote Status Update")
                    .message(message)
                    .build();

                notificationService.sendNotification(notification);
            }
        } catch (Exception e) {
            log.error("Error sending tender status change notifications for quote {}: {}", quote.getId(), e.getMessage(), e);
        }
    }

    private String getTenderStatusChangeMessage(QuoteDTO quote, String newStatus) {
        switch (newStatus.toLowerCase()) {
            case "approved":
                return String.format(
                    "Tender Quote (ID: %s) has been automatically approved. The expected fulfillment start date (%s) has been reached.",
                    quote.getId(),
                    quote.getExpectedFulfillmentStartDate()
                );
            case "accepted":
                return String.format(
                    "Tender Quote (ID: %s) has been automatically accepted. The effective quote completion date (%s) has been reached.",
                    quote.getId(),
                    quote.getEffectiveQuoteCompletionDate()
                );
            default:
                return String.format(
                    "Tender Quote (ID: %s) status has been updated to: %s",
                    quote.getId(),
                    newStatus
                );
        }
    }

    private void handleExpiredQuote(QuoteDTO quote) {
        log.info("Handling expired quote: {}", quote.getId());
        try {
            // Update quote status to cancelled
            String url = tmforumBaseUrl.trim() + appConfig.getTmforumQuoteEndpoint() + "/" + quote.getId();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");

            // Create status update payload
            String jsonPayload = buildStatusUpdateJson("cancelled", quote);
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

            restTemplate.exchange(url, org.springframework.http.HttpMethod.PATCH, request, QuoteDTO.class);

            // Add note about expiration
            String noteUrl = tmforumBaseUrl.trim() + appConfig.getTmforumQuoteEndpoint() + "/" + quote.getId();
            String notePayload = buildNoteUpdateJson(
                "Quote automatically cancelled due to expiration of requested completion date.",
                "SYSTEM",
                quote
            );
            HttpEntity<String> noteRequest = new HttpEntity<>(notePayload, headers);
            restTemplate.exchange(noteUrl, org.springframework.http.HttpMethod.PATCH, noteRequest, QuoteDTO.class);

            // Send notification to customer and provider
            sendExpirationNotifications(quote);

            log.info("Successfully handled expired quote: {}", quote.getId());
        } catch (Exception e) {
            log.error("Error handling expired quote {}: {}", quote.getId(), e.getMessage(), e);
        }
    }

    private void sendExpirationNotifications(QuoteDTO quote) {
        try {
            // Find customer and provider IDs
            String customerId = quote.getRelatedParty().stream()
                .filter(party -> "customer".equals(party.getRole()))
                .findFirst()
                .map(party -> party.getId())
                .orElse(null);

            String providerId = quote.getRelatedParty().stream()
                .filter(party -> "seller".equals(party.getRole()))
                .findFirst()
                .map(party -> party.getId())
                .orElse(null);

            if (customerId != null && providerId != null) {
                String message = String.format(
                    "Quote (ID: %s) has been automatically cancelled due to expiration of the requested completion date (%s).",
                    quote.getId(),
                    quote.getRequestedQuoteCompletionDate()
                );

                NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .sender(providerId)
                    .recipient(customerId)
                    .subject("Quote Expired")
                    .message(message)
                    .build();

                notificationService.sendNotification(notification);
            }
        } catch (Exception e) {
            log.error("Error sending expiration notifications for quote {}: {}", quote.getId(), e.getMessage(), e);
        }
    }

    private String buildStatusUpdateJson(String statusValue, QuoteDTO currentQuote) {
        try {
            ObjectNode updateJson = objectMapper.createObjectNode();
            ArrayNode quoteItemArray = objectMapper.createArrayNode();
            
            if (currentQuote.getQuoteItem() != null && !currentQuote.getQuoteItem().isEmpty()) {
                for (QuoteItemDTO quoteItem : currentQuote.getQuoteItem()) {
                    ObjectNode quoteItemJson = objectMapper.createObjectNode();
                    quoteItemJson.put("state", statusValue);
                    
                    if (quoteItem.getId() != null) {
                        quoteItemJson.put("id", quoteItem.getId());
                    }
                    if (quoteItem.getAction() != null) {
                        quoteItemJson.put("action", quoteItem.getAction());
                    }
                    if (quoteItem.getQuantity() != null) {
                        quoteItemJson.put("quantity", quoteItem.getQuantity());
                    }
                    
                    quoteItemArray.add(quoteItemJson);
                }
            }
            
            updateJson.set("quoteItem", quoteItemArray);
            return objectMapper.writeValueAsString(updateJson);
        } catch (Exception e) {
            log.error("Error building status update JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to build status update JSON", e);
        }
    }

    private String buildNoteUpdateJson(String messageContent, String userId, QuoteDTO currentQuote) {
        try {
            ObjectNode updateJson = objectMapper.createObjectNode();
            ArrayNode noteArray = objectMapper.createArrayNode();
            
            if (currentQuote.getNote() != null && !currentQuote.getNote().isEmpty()) {
                for (NoteDTO existingNote : currentQuote.getNote()) {
                    ObjectNode existingNoteObject = objectMapper.createObjectNode();
                    existingNoteObject.put("@type", "Note");
                    if (existingNote.getText() != null) {
                        existingNoteObject.put("text", existingNote.getText());
                    }
                    if (existingNote.getDate() != null) {
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
            
            ObjectNode newNoteObject = objectMapper.createObjectNode();
            newNoteObject.put("@type", "Note");
            newNoteObject.put("text", messageContent);
            newNoteObject.put("date", LocalDateTime.now().toString());
            newNoteObject.put("author", userId);
            
            noteArray.add(newNoteObject);
            updateJson.set("note", noteArray);
            
            return objectMapper.writeValueAsString(updateJson);
        } catch (Exception e) {
            log.error("Error building note update JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to build note update JSON", e);
        }
    }
}