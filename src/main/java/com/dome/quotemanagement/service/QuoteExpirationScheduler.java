package com.dome.quotemanagement.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class QuoteExpirationScheduler {
    private static final Logger logger = LoggerFactory.getLogger(QuoteExpirationScheduler.class);
    private static final String QUOTES_ENDPOINT = "http://localhost:8080/quoteManagement/getAllQuotes";
    private static final String UPDATE_STATUS_ENDPOINT = "http://localhost:8080/quoteManagement/updateQuoteStatus/";
    private static final String ADD_NOTE_ENDPOINT = "http://localhost:8080/quoteManagement/addNoteToQuote/";
    private static final String CANCELLED_STATUS = "cancelled";
    private static final String EXPIRATION_NOTE = "Quote cancelled by the system due to expiration.";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Scheduled task that runs every day at midnight to check for expired quotes
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void checkExpiredQuotes() {
        try {
            logger.info("Starting scheduled check for expired quotes");

            // Fetch all quotes
            List<Map<String, Object>> quotes = restTemplate.getForObject(QUOTES_ENDPOINT, List.class);

            if (quotes == null || quotes.isEmpty()) {
                logger.info("No quotes found to check");
                return;
            }

            LocalDate today = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (Map<String, Object> quote : quotes) {
                try {
                    String quoteId = String.valueOf(quote.get("id"));
                    String currentState = (String) quote.get("state");

                    // Skip if already cancelled
                    if (CANCELLED_STATUS.equalsIgnoreCase(currentState)) {
                        continue;
                    }

                    // Check expectedQuoteCompletionDate first
                    String expectedDateStr = (String) quote.get("expectedQuoteCompletionDate");
                    if (expectedDateStr != null && !expectedDateStr.isEmpty()) {
                        LocalDate expectedDate = LocalDate.parse(expectedDateStr, formatter);
                        if (expectedDate.isBefore(today)) {
                            handleExpiredQuote(quoteId);
                        }
                        continue;
                    }

                    // If expectedQuoteCompletionDate is not present, check requestedQuoteCompletionDate
                    String requestedDateStr = (String) quote.get("requestedQuoteCompletionDate");
                    if (requestedDateStr != null && !requestedDateStr.isEmpty()) {
                        LocalDate requestedDate = LocalDate.parse(requestedDateStr, formatter);
                        if (requestedDate.isBefore(today)) {
                            handleExpiredQuote(quoteId);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error processing quote: {}", quote, e);
                }
            }
        } catch (Exception e) {
            logger.error("Error in checkExpiredQuotes: ", e);
        }
    }

    private void handleExpiredQuote(String quoteId) {
        try {
            // Update quote status to cancelled
            String updateStatusUrl = UPDATE_STATUS_ENDPOINT + quoteId + "?statusValue=" + CANCELLED_STATUS;
            restTemplate.exchange(
                updateStatusUrl,
                HttpMethod.PATCH,
                null,
                Void.class
            );

            // Add note to quote
            String addNoteUrl = ADD_NOTE_ENDPOINT + quoteId + "?userId=system&messageContent=" + EXPIRATION_NOTE;
            restTemplate.exchange(
                addNoteUrl,
                HttpMethod.PATCH,
                null,
                Void.class
            );

            logger.info("Successfully processed expired quote: {}", quoteId);
        } catch (Exception e) {
            logger.error("Error handling expired quote {}: ", quoteId, e);
        }
    }
}