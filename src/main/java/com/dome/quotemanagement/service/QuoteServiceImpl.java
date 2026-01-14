package com.dome.quotemanagement.service;

import com.dome.quotemanagement.config.AppConfig;
import com.dome.quotemanagement.dto.tmforum.AttachmentRefOrValueDTO;
import com.dome.quotemanagement.dto.tmforum.QuoteDTO;
import com.dome.quotemanagement.dto.tmforum.QuoteItemDTO;
import com.dome.quotemanagement.dto.tmforum.NoteDTO;
import com.dome.quotemanagement.dto.NotificationRequestDTO;
import com.dome.quotemanagement.enums.QuoteRole;
import com.dome.quotemanagement.exception.QuoteManagementException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.Collections;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteServiceImpl implements QuoteService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final AppConfig appConfig;
    
    @Value("${attachment.verification.enabled:true}")
    private boolean attachmentVerificationEnabled;
    
    @Value("${attachment.verification.max-attempts:5}")
    private int attachmentVerificationMaxAttempts;
    
    @Value("${attachment.verification.delay-ms:1000}")
    private int attachmentVerificationDelayMs;
    
    @Value("${quote.pagination.page-size:10}")
    private int paginationPageSize;
    
    @Override
    public List<QuoteDTO> findAllQuotes() {
        // Use pagination to avoid ContentLengthExceededException when quotes have heavy attachments
        // Fetch quotes in smaller batches to stay under the 10MB limit
        int pageSize = paginationPageSize; // Small page size to handle heavy quotes with attachments
        int offset = 0;
        List<QuoteDTO> allQuotes = new java.util.ArrayList<>();
        
        String baseUrl = appConfig.getQuoteServiceUrl();
        log.debug("Calling external TMForum API to get all quotes with pagination: {}", baseUrl);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            
            boolean hasMore = true;
            int pageNumber = 0;
            int consecutiveFailures = 0;
            int skippedQuotes = 0;
            final int MAX_CONSECUTIVE_FAILURES = 3; // Stop after 3 consecutive failures
            
            while (hasMore) {
                try {
                    // Build URL with pagination parameters
                    String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                        .queryParam("limit", pageSize)
                        .queryParam("offset", offset)
                        .build(true)
                        .toUriString();
                    
                    log.debug("Fetching quotes page {} (offset={}, limit={}): {}", pageNumber + 1, offset, pageSize, url);
                    
                    HttpEntity<?> request = new HttpEntity<>(headers);
                    
                    var response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        request,
                        QuoteDTO[].class
                    );
                    
                    QuoteDTO[] quotes = response.getBody();
                    
                    // Reset failure counter on success
                    consecutiveFailures = 0;
                    
                    if (quotes == null || quotes.length == 0) {
                        hasMore = false;
                        log.debug("No more quotes found at page {}", pageNumber + 1);
                    } else {
                        allQuotes.addAll(Arrays.asList(quotes));
                        log.debug("Retrieved {} quotes from page {} (total so far: {})", quotes.length, pageNumber + 1, allQuotes.size());
                        
                        // If we got fewer quotes than the page size, we've reached the end
                        if (quotes.length < pageSize) {
                            hasMore = false;
                            log.debug("Reached end of quotes (got {} quotes, expected {})", quotes.length, pageSize);
                        } else {
                            // Move to next page
                            offset += pageSize;
                            pageNumber++;
                        }
                    }
                } catch (org.springframework.web.client.HttpClientErrorException | 
                         org.springframework.web.client.HttpServerErrorException e) {
                    // Check if it's a content length exceeded error (usually 413 or 500 with specific message)
                    String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    boolean isContentLengthError = errorMessage.contains("contentlength") || 
                                                   errorMessage.contains("content length") ||
                                                   errorMessage.contains("413") ||
                                                   (e.getStatusCode() != null && e.getStatusCode().value() == 413);
                    
                    if (isContentLengthError || pageSize > 1) {
                        // If we get a content length error or any error with pageSize > 1, try with pageSize = 1
                        log.warn("Error fetching page {} with pageSize {}: {}. Trying with pageSize=1 for this page.", 
                                pageNumber + 1, pageSize, e.getMessage());
                        
                        try {
                            // Try fetching just one quote at a time for this page
                            String singleQuoteUrl = UriComponentsBuilder.fromHttpUrl(baseUrl)
                                .queryParam("limit", 1)
                                .queryParam("offset", offset)
                                .build(true)
                                .toUriString();
                            
                            log.debug("Retrying with single quote: {}", singleQuoteUrl);
                            
                            HttpEntity<?> singleRequest = new HttpEntity<>(headers);
                            var singleResponse = restTemplate.exchange(
                                singleQuoteUrl,
                                HttpMethod.GET,
                                singleRequest,
                                QuoteDTO[].class
                            );
                            
                            QuoteDTO[] singleQuotes = singleResponse.getBody();
                            if (singleQuotes != null && singleQuotes.length > 0) {
                                allQuotes.addAll(Arrays.asList(singleQuotes));
                                log.info("Successfully retrieved 1 quote individually (total so far: {})", allQuotes.size());
                                
                                // Move to next quote
                                offset += 1;
                                pageNumber++;
                                consecutiveFailures = 0;
                                
                                // Continue with normal page size for next iteration
                                continue;
                            } else {
                                // No quotes returned, we've reached the end
                                hasMore = false;
                                log.debug("No quotes returned with pageSize=1, reached end");
                                break;
                            }
                        } catch (Exception singleException) {
                            // Even single quote failed - this quote is too large, skip it
                            skippedQuotes++;
                            log.error("Even single quote fetch failed at offset {}: {}. Skipping this quote (too large, likely >10MB attachment) and continuing. Total skipped: {}", 
                                    offset, singleException.getMessage(), skippedQuotes);
                            
                            consecutiveFailures++;
                            
                            // Skip this quote and try the next one
                            offset += 1;
                            pageNumber++;
                            
                            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                                log.error("Too many consecutive failures ({}). Stopping pagination to avoid infinite loop.", 
                                        consecutiveFailures);
                                hasMore = false;
                                break;
                            }
                        }
                    } else {
                        // Other type of error, log and continue
                        log.error("Error fetching page {}: {}. Skipping this page.", pageNumber + 1, e.getMessage());
                        consecutiveFailures++;
                        
                        // Skip this page
                        offset += pageSize;
                        pageNumber++;
                        
                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            log.error("Too many consecutive failures ({}). Stopping pagination.", consecutiveFailures);
                            hasMore = false;
                            break;
                        }
                    }
                } catch (Exception e) {
                    // Generic exception handling
                    String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    boolean isContentLengthError = errorMessage.contains("contentlength") || 
                                                   errorMessage.contains("content length") ||
                                                   errorMessage.contains("exceeded");
                    
                    if (isContentLengthError && pageSize > 1) {
                        // Try with pageSize = 1
                        log.warn("Content length error at page {}: {}. Trying with pageSize=1.", 
                                pageNumber + 1, e.getMessage());
                        
                        try {
                            String singleQuoteUrl = UriComponentsBuilder.fromHttpUrl(baseUrl)
                                .queryParam("limit", 1)
                                .queryParam("offset", offset)
                                .build(true)
                                .toUriString();
                            
                            HttpEntity<?> singleRequest = new HttpEntity<>(headers);
                            var singleResponse = restTemplate.exchange(
                                singleQuoteUrl,
                                HttpMethod.GET,
                                singleRequest,
                                QuoteDTO[].class
                            );
                            
                            QuoteDTO[] singleQuotes = singleResponse.getBody();
                            if (singleQuotes != null && singleQuotes.length > 0) {
                                allQuotes.addAll(Arrays.asList(singleQuotes));
                                offset += 1;
                                pageNumber++;
                                consecutiveFailures = 0;
                                continue;
                            }
                        } catch (Exception singleException) {
                            skippedQuotes++;
                            log.error("Single quote fetch also failed: {}. Skipping quote at offset {} (too large, likely >10MB attachment). Total skipped: {}", 
                                    singleException.getMessage(), offset, skippedQuotes);
                            offset += 1;
                            pageNumber++;
                            consecutiveFailures++;
                        }
                    } else {
                        log.error("Unexpected error fetching page {}: {}. Skipping this page.", 
                                pageNumber + 1, e.getMessage(), e);
                        consecutiveFailures++;
                        offset += pageSize;
                        pageNumber++;
                    }
                    
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        log.error("Too many consecutive failures ({}). Stopping pagination.", consecutiveFailures);
                        hasMore = false;
                        break;
                    }
                }
            }
            
            if (skippedQuotes > 0) {
                log.warn("Retrieved {} quotes total using pagination ({} pages), but {} quotes were skipped due to size exceeding 10MB limit (likely due to large attachments)", 
                        allQuotes.size(), pageNumber + 1, skippedQuotes);
            } else {
                log.info("Successfully retrieved {} quotes total using pagination ({} pages)", allQuotes.size(), pageNumber + 1);
            }
            return allQuotes;
            
        } catch (Exception e) {
            log.error("Error calling TMForum API with pagination: {}", e.getMessage(), e);
            // If pagination fails, try fallback to original method with smaller limit
            log.warn("Falling back to single request with limit=10");
            try {
                String fallbackUrl = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("limit", 10)
                    .build(true)
                    .toUriString();
                
                HttpHeaders headers = new HttpHeaders();
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                HttpEntity<?> request = new HttpEntity<>(headers);
                
                var response = restTemplate.exchange(
                    fallbackUrl,
                    HttpMethod.GET,
                    request,
                    QuoteDTO[].class
                );
                
                QuoteDTO[] quotes = response.getBody();
                List<QuoteDTO> result = Arrays.asList(quotes != null ? quotes : new QuoteDTO[0]);
                log.info("Fallback retrieved {} quotes", result.size());
                return result;
            } catch (Exception fallbackException) {
                log.error("Fallback also failed: {}", fallbackException.getMessage());
            return Arrays.asList();
            }
        }
    }
    
    @Override
    public List<QuoteDTO> findQuotesByUser(String userId, String role) {
        // Extract base URL without query parameters
        String listEndpoint = appConfig.getTmforumQuoteListEndpoint();
        String baseUrl = appConfig.getQuoteServiceBaseUrl().trim() + listEndpoint.split("\\?")[0]; // Remove existing query params
        log.debug("Base TMForum list API: {}", baseUrl);
        log.debug("Find tailored quotes parameters - userId: '{}', role: '{}'", userId, role);

        // Validate role
        if (!QuoteRole.equalsIgnoreCase(role, QuoteRole.SELLER) && !QuoteRole.equalsIgnoreCase(role, QuoteRole.CUSTOMER)) {
            log.warn("Invalid role provided: {}", role);
            return Collections.emptyList();
        }

        try {
            // Use pagination helper with server-side filters
            java.util.Map<String, String> queryParams = new java.util.HashMap<>();
            queryParams.put("category", "tailored");
            queryParams.put("relatedParty.id", userId);
            
            List<QuoteDTO> quotes = fetchQuotesWithPagination(baseUrl, queryParams);
            
            // Apply client-side filtering to ensure server-side filters were respected
            List<QuoteDTO> filteredQuotes = quotes.stream()
                .filter(quote -> {
                    boolean userRoleMatch = false;
                    if (QuoteRole.equalsIgnoreCase(role, QuoteRole.SELLER)) {
                        if (quote.getRelatedParty() != null) {
                            userRoleMatch = quote.getRelatedParty().stream()
                                .anyMatch(party -> userId.equals(party.getId()) 
                                    && QuoteRole.equalsIgnoreCase(party.getRole(), QuoteRole.SELLER));
                        }
                    } else if (QuoteRole.equalsIgnoreCase(role, QuoteRole.CUSTOMER)) {
                        if (quote.getRelatedParty() != null) {
                            userRoleMatch = quote.getRelatedParty().stream()
                                .anyMatch(party -> userId.equals(party.getId()) 
                                    && QuoteRole.equalsIgnoreCase(party.getRole(), QuoteRole.CUSTOMER));
                        }
                    }

                    if (!userRoleMatch) {
                        return false;
                    }

                    if (!"tailored".equalsIgnoreCase(quote.getCategory())) {
                        return false;
                    }

                    return true;
                })
                .collect(Collectors.toList());
            
            if (filteredQuotes.size() < quotes.size()) {
                log.warn("Server-side filtering was incomplete: returned {} quotes but only {} match the criteria after client-side validation", 
                    quotes.size(), filteredQuotes.size());
            }
            
            log.info("Client-side validated {} tailored quotes for user '{}', role '{}'", 
                filteredQuotes.size(), userId, role);
            
            return filteredQuotes;

        } catch (Exception e) {
            log.error("Error finding quotes by user: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<QuoteDTO> findTenderingQuotesByUser(String userId, String role, String externalId) {
        // Extract base URL without query parameters
        String listEndpoint = appConfig.getTmforumQuoteListEndpoint();
        String baseUrl = appConfig.getQuoteServiceBaseUrl().trim() + listEndpoint.split("\\?")[0]; // Remove existing query params
        log.debug("Base TMForum list API: {}", baseUrl);
        log.debug("Find tendering quotes parameters - userId: '{}', role: '{}', externalId: '{}'", userId, role, externalId);

        boolean filterByExternalId = externalId != null && !externalId.trim().isEmpty();

        // Validate role
        if (!QuoteRole.equalsIgnoreCase(role, QuoteRole.SELLER) && !QuoteRole.equalsIgnoreCase(role, QuoteRole.CUSTOMER)) {
            log.warn("Invalid role provided: {}", role);
            return Collections.emptyList();
        }

        try {
            // Use pagination helper with server-side filters
            java.util.Map<String, String> queryParams = new java.util.HashMap<>();
            queryParams.put("category", "tender");
            queryParams.put("relatedParty.id", userId);
            if (filterByExternalId) {
                queryParams.put("externalId", externalId);
            }
            
            List<QuoteDTO> quotes = fetchQuotesWithPagination(baseUrl, queryParams);
            
            // Apply client-side filtering to ensure server-side filters were respected
            List<QuoteDTO> filteredQuotes = quotes.stream()
                .filter(quote -> {
                    boolean userRoleMatch = false;
                    if (QuoteRole.equalsIgnoreCase(role, QuoteRole.SELLER)) {
                        if (quote.getRelatedParty() != null) {
                            userRoleMatch = quote.getRelatedParty().stream()
                                .anyMatch(party -> userId.equals(party.getId()) 
                                    && QuoteRole.equalsIgnoreCase(party.getRole(), QuoteRole.SELLER));
                        }
                    } else if (QuoteRole.equalsIgnoreCase(role, QuoteRole.CUSTOMER)) {
                        if (quote.getRelatedParty() != null) {
                            userRoleMatch = quote.getRelatedParty().stream()
                                .anyMatch(party -> userId.equals(party.getId()) 
                                    && QuoteRole.equalsIgnoreCase(party.getRole(), QuoteRole.CUSTOMER));
                        }
                    }

                    if (!userRoleMatch) {
                        return false;
                    }

                    if (!"tender".equalsIgnoreCase(quote.getCategory())) {
                        return false;
                    }

                    if (filterByExternalId && !externalId.equals(quote.getExternalId())) {
                        return false;
                    }

                    return true;
                })
                .collect(Collectors.toList());
            
            if (filteredQuotes.size() < quotes.size()) {
                log.warn("Server-side filtering was incomplete: returned {} quotes but only {} match the criteria after client-side validation", 
                    quotes.size(), filteredQuotes.size());
            }
            
            if (filterByExternalId) {
                log.info("Client-side validated {} tender quotes for user '{}', role '{}', externalId '{}'", 
                    filteredQuotes.size(), userId, role, externalId);
            } else {
                log.info("Client-side validated {} tender quotes for user '{}', role '{}'", 
                    filteredQuotes.size(), userId, role);
            }
            
            return filteredQuotes;

        } catch (Exception e) {
            log.error("Error finding tendering quotes by user: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public List<QuoteDTO> findCoordinatorQuotesByUser(String userId) {
        // Extract base URL without query parameters
        String listEndpoint = appConfig.getTmforumQuoteListEndpoint();
        String baseUrl = appConfig.getQuoteServiceBaseUrl().trim() + listEndpoint.split("\\?")[0]; // Remove existing query params
        log.debug("Base TMForum list API: {}", baseUrl);
        log.debug("Find coordinator quotes parameters - userId: '{}'", userId);

        try {
            // Use pagination helper with server-side filters
            java.util.Map<String, String> queryParams = new java.util.HashMap<>();
            queryParams.put("category", "coordinator");
            queryParams.put("relatedParty.id", userId);
            
            List<QuoteDTO> quotes = fetchQuotesWithPagination(baseUrl, queryParams);
            
            // Apply client-side filtering to ensure server-side filters were respected
            List<QuoteDTO> filteredQuotes = quotes.stream()
                .filter(quote -> {
                    boolean userMatch = false;
                    if (quote.getRelatedParty() != null) {
                        userMatch = quote.getRelatedParty().stream()
                            .anyMatch(party -> userId.equals(party.getId()));
                    }
                    if (!userMatch) {
                        return false;
                    }
                    if (!"coordinator".equals(quote.getCategory())) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
            
            if (filteredQuotes.size() < quotes.size()) {
                log.warn("Server-side filtering was incomplete: returned {} quotes but only {} match the criteria after client-side validation", 
                    quotes.size(), filteredQuotes.size());
            }
            
            log.info("Client-side validated {} coordinator quotes for user '{}'", filteredQuotes.size(), userId);
            return filteredQuotes;

        } catch (Exception e) {
            log.error("Error finding coordinator quotes by user: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public Optional<QuoteDTO> findById(String id) {
        String url = appConfig.getQuoteServiceUrl() + "/" + id;
        log.debug("Calling external TMForum API: {}", url);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<?> request = new HttpEntity<>(headers);
            
            var response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                QuoteDTO.class
            );
            
            QuoteDTO quote = response.getBody();
            log.debug("Received quote from TMForum API: {}", quote);
            
            // Log the raw response for debugging
            log.info("Raw response from TMForum API: {}", response.getBody());
            
            // Log related parties details
            if (quote != null && quote.getRelatedParty() != null) {
                quote.getRelatedParty().forEach(party -> {
                    log.info("Related party details - ID: {}, Role: {}, Type: {}, BaseType: {}, ReferredType: {}", 
                        party.getId(), 
                        party.getRole(),
                        party.getType(),
                        party.getBaseType(),
                        party.getReferredType());
                });
            }
            
            return Optional.ofNullable(quote);
        } catch (Exception e) {
            log.error("Error calling TMForum API: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }
    
    @Override
    public QuoteDTO create(String customerMessage, String customerIdRef, String providerIdRef, String productOfferingId) {
        log.debug("Creating tailored quote with parameters - customerMessage: '{}', customerIdRef: '{}', providerIdRef: '{}', productOfferingId: '{}'", 
                  customerMessage, customerIdRef, providerIdRef, productOfferingId);
        
        // For tailored quotes: category="tailored" (hardcoded), externalId="0000" (not significant)
        return createInternal(customerMessage, customerIdRef, providerIdRef, productOfferingId, "tailored", "0000");
    }
    
    private QuoteDTO createInternal(String customerMessage, String customerIdRef, String providerIdRef, String productOfferingId, String category, String externalId) {
        String url = appConfig.getQuoteServiceUrl();
        log.debug("Calling external TMForum API: {}", url);
        log.debug("Create quote parameters - customerMessage: '{}', customerIdRef: '{}', providerIdRef: '{}', productOfferingId: '{}', category: '{}', externalId: '{}'", 
                  customerMessage, customerIdRef, providerIdRef, productOfferingId, category, externalId);
        
        // TODO: TEMPORARILY COMMENTED OUT FOR LOCAL TESTING - MUST BE RESTORED BEFORE PRODUCTION
        // Check if providerIdRef is equal to customerIdRef
        /* COMMENTED FOR TESTING - RESTORE THIS VALIDATION
        if (providerIdRef != null && customerIdRef != null && providerIdRef.equals(customerIdRef)) {
            throw new QuoteManagementException(
                "The user cannot request a quote for its own service!", 
                HttpStatus.CONFLICT
            );
        }
        */
        // END TODO: RESTORE VALIDATION ABOVE
        
        try {
            // Build a minimal JSON payload that conforms to TMForum standards
            String jsonPayload = buildCreateQuoteJson(customerMessage, customerIdRef, providerIdRef, productOfferingId, category, externalId);
            
            // Set proper headers for JSON
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
            
            log.info("Sending JSON payload to TMForum API: {}", jsonPayload);
            
            QuoteDTO response = restTemplate.postForObject(url, request, QuoteDTO.class);
            log.info("Received response from TMForum API: {}", response);
            
            // Log the related parties from the response
            if (response != null) {
                log.info("Quote created with ID: {}", response.getId());
                if (response.getRelatedParty() != null) {
                    log.info("Related parties in response: {}", response.getRelatedParty());
                    // Log each related party's details
                    response.getRelatedParty().forEach(party -> {
                        log.info("Related party - ID: {}, Role: {}, Type: {}", 
                            party.getId(), party.getRole(), party.getReferredType());
                    });
                } else {
                    log.warn("No related parties in response for quote: {}", response.getId());
                }
            }

            // Send notification after successful quote creation
            if (response != null && response.getId() != null) {
                NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .sender(customerIdRef)
                    .recipient(providerIdRef)
                    .subject("New Quote Created")
                    .message("New quote created with ID: " + response.getId() + 
                            (customerMessage != null ? "\nMessage: " + customerMessage : ""))
                    .build();
                
                notificationService.sendNotification(notification);
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("Error creating quote: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create quote", e);
        }
    }
    
    @Override
    public QuoteDTO createTenderingQuote(String customerMessage, String customerIdRef, String providerIdRef, String externalId) {
        log.debug("Creating tendering quote with parameters - customerMessage: '{}', customerIdRef: '{}', providerIdRef: '{}', externalId: '{}'", 
                  customerMessage, customerIdRef, providerIdRef, externalId);
        
        // For tendering quotes: category="tender" (hardcoded), externalId from parameter, no productOfferingId needed
        return createInternal(customerMessage, customerIdRef, providerIdRef, null, "tender", externalId);
    }
    
    @Override
    public QuoteDTO createCoordinatorQuote(String customerMessage, String customerIdRef) {
        log.debug("Creating coordinator quote with parameters - customerMessage: '{}', customerIdRef: '{}'", 
                  customerMessage, customerIdRef);
        
        String url = appConfig.getQuoteServiceUrl();
        log.debug("Calling external TMForum API for coordinator quote: {}", url);
        
        try {
            // Build a minimal JSON payload for coordinator quote
            String jsonPayload = buildCoordinatorQuoteJson(customerMessage, customerIdRef);
            
            // Set proper headers for JSON
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
            
            log.info("Sending coordinator quote JSON payload to TMForum API: {}", jsonPayload);
            
            QuoteDTO response = restTemplate.postForObject(url, request, QuoteDTO.class);
            log.info("Received coordinator quote response from TMForum API: {}", response);
            
            if (response != null) {
                log.info("Coordinator quote created with ID: {}", response.getId());
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("Error creating coordinator quote: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create coordinator quote", e);
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
            
            String url = appConfig.getQuoteServiceUrl() + "/" + quoteId;
            
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

            // Send notifications after successful status update
            if (updatedQuote != null) {
                sendStatusUpdateNotifications(updatedQuote, statusValue);
            }
            
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
            
            String url = appConfig.getQuoteServiceUrl() + "/" + quoteId;
            
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

            // Send notification after successful note update
            if (updatedQuote != null) {
                sendNoteUpdateNotification(updatedQuote, userId, messageContent);
            }
            
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
            
            // Validate file size (limit to 100MB)
            long maxSize = 100 * 1024 * 1024; // 100MB
            if (file.getSize() > maxSize) {
                throw new IllegalArgumentException("File size exceeds maximum allowed size of 100MB");
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
            
            String url = appConfig.getQuoteServiceUrl() + "/" + quoteId;
            
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

            // CRITICAL: Wait until the attachment is actually persisted before returning success
            // The TMForum API might return success before async processing completes
            if (updatedQuote != null && attachmentVerificationEnabled) {
                log.info("Waiting for attachment to be persisted before returning success...");
                waitForAttachmentPersistence(quoteId, file.getOriginalFilename(), attachmentVerificationMaxAttempts, attachmentVerificationDelayMs);
                log.info("Attachment persistence confirmed for quote: {} and file: {}", quoteId, file.getOriginalFilename());
            }

            // Send notification to customer about the new document
            if (updatedQuote != null) {
                // Find customer ID from quote.relatedParty where role is "Customer"
                String customerId = null;
                if (updatedQuote.getRelatedParty() != null) {
                    customerId = updatedQuote.getRelatedParty().stream()
                        .filter(party -> QuoteRole.isCustomer(party.getRole()))
                        .findFirst()
                        .map(party -> party.getId())
                        .orElse(null);
                }

                // Find provider ID from quote.relatedParty where role is "Seller"
                String providerId = null;
                if (updatedQuote.getRelatedParty() != null) {
                    providerId = updatedQuote.getRelatedParty().stream()
                        .filter(party -> QuoteRole.isSeller(party.getRole()))
                        .findFirst()
                        .map(party -> party.getId())
                        .orElse(null);
                }

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
                        .sender(providerId)
                        .recipient(customerId)
                        .subject("New Document Uploaded")
                        .message(message)
                        .build();

                    notificationService.sendNotification(notification);
                    
                    log.info("Notification sent to customer {} about new document upload for quote {}", customerId, quoteId);
                } else {
                    log.warn("Could not send notification - customerId: {}, providerId: {} (relatedParty data may be missing from PATCH response)", customerId, providerId);
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
    public Optional<QuoteDTO> updateQuoteDate(String quoteId, String date, String dateType) {
        log.debug("Updating quote date - quoteId: '{}', date: '{}', dateType: '{}'", quoteId, date, dateType);
        
        try {
            // First, get the current quote
            Optional<QuoteDTO> currentQuoteOpt = findById(quoteId);
            if (currentQuoteOpt.isEmpty()) {
                log.warn("Quote not found with id: {}", quoteId);
                return Optional.empty();
            }
            
            // Validate dateType
            if (!"requested".equalsIgnoreCase(dateType) && !"expected".equalsIgnoreCase(dateType) && 
                !"effective".equalsIgnoreCase(dateType) && !"expectedFulfillment".equalsIgnoreCase(dateType)) {
                log.error("Invalid dateType: {}. Must be one of: 'requested', 'expected', 'effective', 'expectedFulfillment'", dateType);
                throw new IllegalArgumentException("Invalid dateType. Must be one of: 'requested', 'expected', 'effective', 'expectedFulfillment'");
            }
            
            // Parse the user-friendly DD-MM-YYYY format and convert to ISO 8601
            String isoDate = convertDateToISO8601(date);
            
            // Create a minimal update payload with the converted date
            String jsonPayload = buildDateUpdateJson(isoDate, dateType);
            
            String url = appConfig.getQuoteServiceUrl() + "/" + quoteId;
            
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
        String url = appConfig.getQuoteServiceUrl() + "/" + id;
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
                    
                    // CRITICAL: Preserve relatedParty information to maintain customer filtering
                    if (quoteItem.getRelatedParty() != null && !quoteItem.getRelatedParty().isEmpty()) {
                        ArrayNode relatedPartyArray = objectMapper.createArrayNode();
                        for (com.dome.quotemanagement.dto.tmforum.RelatedPartyDTO relatedParty : quoteItem.getRelatedParty()) {
                            ObjectNode relatedPartyObject = objectMapper.createObjectNode();
                            if (relatedParty.getId() != null) {
                                relatedPartyObject.put("id", relatedParty.getId());
                            }
                            if (relatedParty.getHref() != null) {
                                relatedPartyObject.put("href", relatedParty.getHref());
                            }
                            if (relatedParty.getRole() != null) {
                                relatedPartyObject.put("role", relatedParty.getRole());
                            }
                            if (relatedParty.getReferredType() != null) {
                                relatedPartyObject.put("@referredType", relatedParty.getReferredType());
                            }
                            if (relatedParty.getType() != null) {
                                relatedPartyObject.put("@type", relatedParty.getType());
                            }
                            relatedPartyArray.add(relatedPartyObject);
                        }
                        quoteItemJson.set("relatedParty", relatedPartyArray);
                    }
                    
                    // CRITICAL: Preserve attachment information to prevent loss during status updates
                    if (quoteItem.getAttachment() != null && !quoteItem.getAttachment().isEmpty()) {
                        ArrayNode attachmentArray = objectMapper.createArrayNode();
                        for (AttachmentRefOrValueDTO attachment : quoteItem.getAttachment()) {
                            ObjectNode attachmentObject = objectMapper.createObjectNode();
                            attachmentObject.put("@type", "AttachmentRefOrValue");
                            
                            if (attachment.getId() != null) {
                                attachmentObject.put("id", attachment.getId());
                            }
                            if (attachment.getName() != null) {
                                attachmentObject.put("name", attachment.getName());
                            }
                            if (attachment.getDescription() != null) {
                                attachmentObject.put("description", attachment.getDescription());
                            }
                            if (attachment.getMimeType() != null) {
                                attachmentObject.put("mimeType", attachment.getMimeType());
                            }
                            if (attachment.getContent() != null) {
                                attachmentObject.put("content", attachment.getContent());
                            }
                            if (attachment.getSize() != null) {
                                ObjectNode sizeObject = objectMapper.createObjectNode();
                                sizeObject.put("amount", attachment.getSize().getAmount());
                                sizeObject.put("units", attachment.getSize().getUnits());
                                attachmentObject.set("size", sizeObject);
                            }
                            if (attachment.getUrl() != null) {
                                attachmentObject.put("url", attachment.getUrl());
                            }
                            
                            attachmentArray.add(attachmentObject);
                        }
                        quoteItemJson.set("attachment", attachmentArray);
                        log.debug("Preserved {} attachments for quote item {}", attachmentArray.size(), quoteItem.getId());
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
                
                // CRITICAL: Preserve relatedParty information to maintain customer filtering
                if (firstQuoteItem.getRelatedParty() != null && !firstQuoteItem.getRelatedParty().isEmpty()) {
                    ArrayNode relatedPartyArray = objectMapper.createArrayNode();
                    for (com.dome.quotemanagement.dto.tmforum.RelatedPartyDTO relatedParty : firstQuoteItem.getRelatedParty()) {
                        ObjectNode relatedPartyObject = objectMapper.createObjectNode();
                        if (relatedParty.getId() != null) {
                            relatedPartyObject.put("id", relatedParty.getId());
                        }
                        if (relatedParty.getHref() != null) {
                            relatedPartyObject.put("href", relatedParty.getHref());
                        }
                        if (relatedParty.getRole() != null) {
                            relatedPartyObject.put("role", relatedParty.getRole());
                        }
                        if (relatedParty.getReferredType() != null) {
                            relatedPartyObject.put("@referredType", relatedParty.getReferredType());
                        }
                        if (relatedParty.getType() != null) {
                            relatedPartyObject.put("@type", relatedParty.getType());
                        }
                        relatedPartyArray.add(relatedPartyObject);
                    }
                    quoteItemJson.set("relatedParty", relatedPartyArray);
                }
                
                // Create attachment array for this quote item (overwrite any existing attachments)
                ArrayNode attachmentArray = objectMapper.createArrayNode();
                
                // Add the new attachment (this will overwrite any existing attachment)
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
    private String buildDateUpdateJson(String date, String dateType) {
        try {
            ObjectNode updateJson = objectMapper.createObjectNode();
            if ("requested".equalsIgnoreCase(dateType)) {
                updateJson.put("requestedQuoteCompletionDate", date);
            } else if ("expected".equalsIgnoreCase(dateType)) {
                updateJson.put("expectedQuoteCompletionDate", date);
            } else if ("effective".equalsIgnoreCase(dateType)) {
                updateJson.put("effectiveQuoteCompletionDate", date);
            } else if ("expectedFulfillment".equalsIgnoreCase(dateType)) {
                updateJson.put("expectedFulfillmentStartDate", date);
            } else {
                throw new IllegalArgumentException("Invalid dateType: " + dateType);
            }
            return objectMapper.writeValueAsString(updateJson);
        } catch (Exception e) {
            log.error("Error building date update JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to build date update JSON", e);
        }
    }
    
    /**
     * Build a minimal JSON payload for coordinator quote creation
     */
    private String buildCoordinatorQuoteJson(String customerMessage, String customerIdRef) {
        try {
            // Create the root JSON object
            ObjectNode quoteJson = objectMapper.createObjectNode();
            
            // Add description if provided
            if (customerMessage != null && !customerMessage.trim().isEmpty()) {
                quoteJson.put("description", customerMessage);
            }
            
            // Add category for coordinator quote
            quoteJson.put("category", "coordinator");
            
            // Add at least one quoteItem as required by the API (minimal structure for coordinator)
            ArrayNode quoteItemArray = objectMapper.createArrayNode();
            ObjectNode quoteItem = objectMapper.createObjectNode();
            quoteItem.put("@type", "QuoteItem");
            quoteItem.put("action", "add");
            quoteItem.put("state", "pending");
            quoteItem.put("quantity", 1);
            quoteItem.set("note", objectMapper.createObjectNode()); // Empty note object
            
            // Add customer and buyer operator as relatedParty at the quote level
            ArrayNode relatedPartyArray = objectMapper.createArrayNode();
            if (customerIdRef != null && !customerIdRef.trim().isEmpty()) {
                ObjectNode customerParty = objectMapper.createObjectNode();
                customerParty.put("@type", "RelatedParty");
                customerParty.put("id", customerIdRef);
                customerParty.put("href", customerIdRef);
                customerParty.put("role", QuoteRole.CUSTOMER);
                customerParty.put("name", customerIdRef);
                customerParty.put("@referredType", "organization");
                relatedPartyArray.add(customerParty);

                String buyerOperatorId = appConfig.getDidIdentifier();
                
                ObjectNode buyerOperator = objectMapper.createObjectNode();
                buyerOperator.put("id", buyerOperatorId);
                buyerOperator.put("href", buyerOperatorId);
                buyerOperator.put("role", QuoteRole.BUYER_OPERATOR);
                buyerOperator.put("name", buyerOperatorId);
                buyerOperator.put("@referredType", "organization");
                relatedPartyArray.add(buyerOperator);
            }
            if (relatedPartyArray.size() > 0) {
                quoteJson.set("relatedParty", relatedPartyArray);
            }
            
            quoteItemArray.add(quoteItem);
            quoteJson.set("quoteItem", quoteItemArray);
            
            // Log the final JSON for debugging
            String jsonPayload = objectMapper.writeValueAsString(quoteJson);
            log.info("Created coordinator quote JSON payload: {}", jsonPayload);
            
            return jsonPayload;
            
        } catch (Exception e) {
            log.error("Error building coordinator quote JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to build coordinator quote JSON", e);
        }
    }
    
    /**
     * Build a minimal JSON payload that conforms to TMForum Quote creation standards
     */
    private String buildCreateQuoteJson(String customerMessage, String customerIdRef, String providerIdRef, String productOfferingId, String category, String externalId) {
        try {
            // Create the root JSON object
            ObjectNode quoteJson = objectMapper.createObjectNode();
            
            // Add description if provided
            if (customerMessage != null && !customerMessage.trim().isEmpty()) {
                quoteJson.put("description", customerMessage);
            }
            
            // Add category if provided
            if (category != null && !category.trim().isEmpty()) {
                quoteJson.put("category", category);
            }
            
            // Add externalId if provided
            if (externalId != null && !externalId.trim().isEmpty()) {
                quoteJson.put("externalId", externalId);
            }
            
            // Add relatedParty array at the Quote level for Seller, SellerOperator, Customer and BuyerOperator
            ArrayNode relatedPartyArray = objectMapper.createArrayNode();
            
            // Add provider related party if provided
            if (providerIdRef != null && !providerIdRef.trim().isEmpty()) {
                ObjectNode providerParty = objectMapper.createObjectNode();
                providerParty.put("id", providerIdRef);
                providerParty.put("href", providerIdRef);
                providerParty.put("role", QuoteRole.SELLER);
                providerParty.put("name", providerIdRef);
                providerParty.put("@referredType", "organization");
                relatedPartyArray.add(providerParty);
            }

            // Resolve SellerOperator from ProductOffering.relatedParty(role=SellerOperator); fallback to config DID
            String sellerOperatorId = resolveSellerOperatorIdFromProductOffering(productOfferingId)
                .orElse(appConfig.getDidIdentifier());

            ObjectNode sellerOperator = objectMapper.createObjectNode();
            sellerOperator.put("id", sellerOperatorId);
            sellerOperator.put("href", sellerOperatorId);
            sellerOperator.put("role", QuoteRole.SELLER_OPERATOR);
            sellerOperator.put("name", sellerOperatorId);
            sellerOperator.put("@referredType", "organization");
            relatedPartyArray.add(sellerOperator);
            log.info("Added SellerOperator to quote-level relatedParty array with ID: {} (source: {})",
                sellerOperatorId,
                (sellerOperatorId.equals(appConfig.getDidIdentifier()) ? "config" : "productOffering"));
            
            // Add customer and buyer operator on the quote-level relatedParty
            if (customerIdRef != null && !customerIdRef.trim().isEmpty()) {
                ObjectNode customerParty = objectMapper.createObjectNode();
                customerParty.put("@type", "RelatedParty");
                customerParty.put("id", customerIdRef);
                customerParty.put("href", customerIdRef);
                customerParty.put("role", QuoteRole.CUSTOMER);
                customerParty.put("name", customerIdRef);
                customerParty.put("@referredType", "organization");
                relatedPartyArray.add(customerParty);

                String buyerOperatorId = appConfig.getDidIdentifier();
                
                ObjectNode buyerOperator = objectMapper.createObjectNode();
                buyerOperator.put("id", buyerOperatorId);
                buyerOperator.put("href", buyerOperatorId);
                buyerOperator.put("role", QuoteRole.BUYER_OPERATOR);
                buyerOperator.put("name", buyerOperatorId);
                buyerOperator.put("@referredType", "organization");
                relatedPartyArray.add(buyerOperator);
            }

            quoteJson.set("relatedParty", relatedPartyArray);
            log.info("Set quote-level relatedParty array with {} entries", relatedPartyArray.size());
            
            // Add at least one quoteItem as required by the API
            ArrayNode quoteItemArray = objectMapper.createArrayNode();
            ObjectNode quoteItem = objectMapper.createObjectNode();
            quoteItem.put("@type", "QuoteItem");
            quoteItem.put("action", "add");
            quoteItem.put("state", "pending");
            quoteItem.put("quantity", 1);
            quoteItem.set("note", objectMapper.createObjectNode()); // Empty note object
            
            // No quoteItem.relatedParty anymore; Customer and BuyerOperator are set at quote level above

            
            // Add productOffering reference if productOfferingId is provided
            if (productOfferingId != null && !productOfferingId.trim().isEmpty()) {
                ObjectNode productOffering = objectMapper.createObjectNode();
                productOffering.put("id", productOfferingId);
                productOffering.put("@type", "ProductOfferingRef");
                quoteItem.set("productOffering", productOffering);
            }
            
            quoteItemArray.add(quoteItem);
            quoteJson.set("quoteItem", quoteItemArray);
            
            // Log the final JSON for debugging
            String jsonPayload = objectMapper.writeValueAsString(quoteJson);
            log.info("Created quote JSON payload: {}", jsonPayload);
            
            return jsonPayload;
            
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

    /**
     * Send notification when a note is added to a quote
     */
    private void sendNoteUpdateNotification(QuoteDTO quote, String senderId, String messageContent) {
        try {
            // Find the recipient from the relatedParty of the quote
            // The recipient should be the other party (not the sender)
            String recipientId = null;
            
            // Check if sender is the customer (now in quote.relatedParty)
            boolean senderIsCustomer = quote.getRelatedParty() != null &&
                quote.getRelatedParty().stream()
                    .anyMatch(party -> senderId.equals(party.getId()) && QuoteRole.isCustomer(party.getRole()));
            
            if (senderIsCustomer) {
                // If sender is customer, recipient is seller (from quote.relatedParty)
                if (quote.getRelatedParty() != null && !quote.getRelatedParty().isEmpty()) {
                    recipientId = quote.getRelatedParty().stream()
                        .filter(party -> QuoteRole.isSeller(party.getRole()))
                        .findFirst()
                        .map(party -> party.getId())
                        .orElse(null);
                }
            } else {
                // If sender is not customer, check if it's seller (from quote.relatedParty)
                boolean senderIsSeller = quote.getRelatedParty() != null && 
                    quote.getRelatedParty().stream()
                        .anyMatch(party -> senderId.equals(party.getId()) && QuoteRole.isSeller(party.getRole()));
                
                if (senderIsSeller) {
                    // If sender is seller, recipient is customer (now from quote.relatedParty)
                    if (quote.getRelatedParty() != null && !quote.getRelatedParty().isEmpty()) {
                        recipientId = quote.getRelatedParty().stream()
                            .filter(party -> QuoteRole.isCustomer(party.getRole()))
                            .findFirst()
                            .map(party -> party.getId())
                            .orElse(null);
                    }
                }
            }
            
            // Send notification if we found a recipient
            if (recipientId != null) {
                String subject = "New Note Added to Quote";
                String message = String.format(
                    "A new note has been added to quote (ID: %s):\n\n%s\n\nSent by: %s",
                    quote.getId(),
                    messageContent,
                    senderId
                );
                
                NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .sender(senderId)
                    .recipient(recipientId)
                    .subject(subject)
                    .message(message)
                    .build();
                
                notificationService.sendNotification(notification);
                log.info("Sent note update notification - sender: {}, recipient: {}, quoteId: {}", 
                    senderId, recipientId, quote.getId());
            } else {
                log.warn("Could not determine recipient for note notification - sender: {}, quoteId: {}", 
                    senderId, quote.getId());
            }
            
        } catch (Exception e) {
            log.error("Error sending note update notification for quote {}: {}", quote.getId(), e.getMessage(), e);
        }
    }

    /**
     * Send notifications to customer and seller when quote status is updated
     */
    private void sendStatusUpdateNotifications(QuoteDTO quote, String newStatus) {
        try {
            // Find customer ID from quote.relatedParty where role is "Customer"
            String customerId = null;
            if (quote.getRelatedParty() != null && !quote.getRelatedParty().isEmpty()) {
                customerId = quote.getRelatedParty().stream()
                    .filter(party -> QuoteRole.isCustomer(party.getRole()))
                    .findFirst()
                    .map(party -> party.getId())
                    .orElse(null);
            }

            // Find seller ID from quote.relatedParty where role is "Seller"
            String sellerId = null;
            if (quote.getRelatedParty() != null && !quote.getRelatedParty().isEmpty()) {
                sellerId = quote.getRelatedParty().stream()
                    .filter(party -> QuoteRole.isSeller(party.getRole()))
                    .findFirst()
                    .map(party -> party.getId())
                    .orElse(null);
            }

            // Create status-specific messages
            String statusMessage = getStatusUpdateMessage(newStatus);
            String subject = "Quote Status Updated - " + newStatus;

            // Send notification to customer
            if (customerId != null && sellerId != null) {
                NotificationRequestDTO customerNotification = NotificationRequestDTO.builder()
                    .sender(sellerId)
                    .recipient(customerId)
                    .subject(subject)
                    .message(String.format(
                        "Your quote (ID: %s) status has been updated to: %s\n\n%s",
                        quote.getId(),
                        newStatus,
                        statusMessage
                    ))
                    .build();

                notificationService.sendNotification(customerNotification);
                log.info("Status update notification sent to customer {} for quote {}", customerId, quote.getId());
            } else {
                log.warn("Could not send customer notification - customerId: {}, sellerId: {}", customerId, sellerId);
            }

            // Send notification to seller
            if (sellerId != null && customerId != null) {
                NotificationRequestDTO sellerNotification = NotificationRequestDTO.builder()
                    .sender(customerId)
                    .recipient(sellerId)
                    .subject(subject)
                    .message(String.format(
                        "Quote (ID: %s) status has been updated to: %s\n\n%s",
                        quote.getId(),
                        newStatus,
                        statusMessage
                    ))
                    .build();

                notificationService.sendNotification(sellerNotification);
                log.info("Status update notification sent to seller {} for quote {}", sellerId, quote.getId());
            } else {
                log.warn("Could not send seller notification - customerId: {}, sellerId: {}", customerId, sellerId);
            }

        } catch (Exception e) {
            log.error("Error sending status update notifications: {}", e.getMessage(), e);
        }
    }

    /**
     * Get a user-friendly message based on the status update
     */
    private String getStatusUpdateMessage(String status) {
        switch (status.toLowerCase()) {
            case "inprogress":
                return "The quote is currently being processed by our sales team. We are working on building the quote according to your requirements.";
            case "pending":
                return "The quote is pending validation from our perspective for tariff validation or to capture detailed information.";
            case "approved":
                return "The quote has been internally approved and is ready for your review. The quote is no longer updatable.";
            case "cancelled":
                return "The quote process has been stopped. This quote has never been sent to the customer.";
            case "accepted":
                return "The quote has been accepted and signed by the customer. The order has been committed.";
            case "rejected":
                return "The quote has been rejected by the customer. No further quotes will be initiated from this quotation.";
            default:
                return "The quote status has been updated. Please review the quote for more details.";
        }
    }

    private Optional<String> resolveSellerOperatorIdFromProductOffering(String productOfferingId) {
        try {
            if (productOfferingId == null || productOfferingId.trim().isEmpty()) {
                return Optional.empty();
            }

            String url = appConfig.getProductServiceUrl().replaceAll("/+$", "") + "/" + productOfferingId.trim();

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<?> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                String.class
            );

            String body = response.getBody();
            if (body == null || body.isEmpty()) {
                log.warn("Empty ProductOffering response for id {}", productOfferingId);
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode relatedParties = root.get("relatedParty");
            if (relatedParties != null && relatedParties.isArray()) {
                for (JsonNode rp : relatedParties) {
                    String role = rp.hasNonNull("role") ? rp.get("role").asText() : null;
                    if (role != null && QuoteRole.equalsIgnoreCase(role, QuoteRole.SELLER_OPERATOR)) {
                        String id = rp.hasNonNull("id") ? rp.get("id").asText() : null;
                        if (id != null && !id.trim().isEmpty()) {
                            return Optional.of(id.trim());
                        }
                    }
                }
            }

            log.warn("SellerOperator not found in ProductOffering.relatedParty for id {}", productOfferingId);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to resolve SellerOperator from ProductOffering {}: {}", productOfferingId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolve organization name from externalReference.name by calling the Organization API
     * @param organizationId the organization ID to look up
     * @return the name from externalReference.name
     * @throws QuoteManagementException if the organization is not found or the name cannot be resolved
     */
    /*
    private String resolveOrganizationName(String organizationId) {
        try {
            if (organizationId == null || organizationId.trim().isEmpty()) {
                throw new QuoteManagementException(
                    "Organization ID cannot be null or empty",
                    HttpStatus.BAD_REQUEST
                );
            }

            String url = appConfig.getPartyServiceUrl().replaceAll("/+$", "") + "/" + organizationId.trim();

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            HttpEntity<?> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                String.class
            );

            String body = response.getBody();
            if (body == null || body.isEmpty()) {
                throw new QuoteManagementException(
                    "Organization not found or empty response for id: " + organizationId,
                    HttpStatus.NOT_FOUND
                );
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode externalReferences = root.get("externalReference");
            
            if (externalReferences != null && externalReferences.isArray()) {
                for (JsonNode extRef : externalReferences) {
                    if (extRef.hasNonNull("name")) {
                        String name = extRef.get("name").asText();
                        if (name != null && !name.trim().isEmpty()) {
                            log.info("Found organization name '{}' from externalReference for id {}", name, organizationId);
                            return name.trim();
                        }
                    }
                }
            }

            throw new QuoteManagementException(
                "externalReference.name not found in Organization for id: " + organizationId,
                HttpStatus.NOT_FOUND
            );
        } catch (QuoteManagementException e) {
            throw e; // Re-throw our custom exceptions
        } catch (Exception e) {
            throw new QuoteManagementException(
                "Failed to resolve organization name for id " + organizationId + ": " + e.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                e
            );
        }
    }
    */

    private void writeLogToFile(String content) {
        try {
            java.nio.file.Files.write(
                java.nio.file.Paths.get("quote_search_debug.log"),
                content.getBytes(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception e) {
            log.error("Error writing debug log to file: {}", e.getMessage(), e);
        }
    }

    /**
     * Helper method to fetch quotes with pagination to avoid ContentLengthExceededException
     * Supports optional query parameters for filtering
     */
    private List<QuoteDTO> fetchQuotesWithPagination(String baseUrl, java.util.Map<String, String> queryParams) {
        int pageSize = paginationPageSize;
        int offset = 0;
        List<QuoteDTO> allQuotes = new java.util.ArrayList<>();
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            
            boolean hasMore = true;
            int pageNumber = 0;
            int consecutiveFailures = 0;
            int skippedQuotes = 0;
            final int MAX_CONSECUTIVE_FAILURES = 3;
            
            while (hasMore) {
                try {
                    UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                        .queryParam("limit", pageSize)
                        .queryParam("offset", offset);
                    
                    // Add optional query parameters
                    if (queryParams != null) {
                        queryParams.forEach(builder::queryParam);
                    }
                    
                    String url = builder.build(true).toUriString();
                    
                    log.debug("Fetching quotes page {} (offset={}, limit={}): {}", pageNumber + 1, offset, pageSize, url);
                    
                    HttpEntity<?> request = new HttpEntity<>(headers);
                    
                    var response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        request,
                        QuoteDTO[].class
                    );
                    
                    QuoteDTO[] quotes = response.getBody();
                    consecutiveFailures = 0;
                    
                    if (quotes == null || quotes.length == 0) {
                        hasMore = false;
                        log.debug("No more quotes found at page {}", pageNumber + 1);
                    } else {
                        allQuotes.addAll(Arrays.asList(quotes));
                        log.debug("Retrieved {} quotes from page {} (total so far: {})", quotes.length, pageNumber + 1, allQuotes.size());
                        
                        if (quotes.length < pageSize) {
                            hasMore = false;
                            log.debug("Reached end of quotes (got {} quotes, expected {})", quotes.length, pageSize);
                        } else {
                            offset += pageSize;
                            pageNumber++;
                        }
                    }
                } catch (org.springframework.web.client.HttpClientErrorException | 
                         org.springframework.web.client.HttpServerErrorException e) {
                    String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    boolean isContentLengthError = errorMessage.contains("contentlength") || 
                                                   errorMessage.contains("content length") ||
                                                   errorMessage.contains("413") ||
                                                   (e.getStatusCode() != null && e.getStatusCode().value() == 413);
                    
                    if (isContentLengthError || pageSize > 1) {
                        log.warn("Error fetching page {} with pageSize {}: {}. Trying with pageSize=1 for this page.", 
                                pageNumber + 1, pageSize, e.getMessage());
                        
                        try {
                            UriComponentsBuilder singleBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                                .queryParam("limit", 1)
                                .queryParam("offset", offset);
                            
                            if (queryParams != null) {
                                queryParams.forEach(singleBuilder::queryParam);
                            }
                            
                            String singleQuoteUrl = singleBuilder.build(true).toUriString();
                            
                            log.debug("Retrying with single quote: {}", singleQuoteUrl);
                            
                            HttpEntity<?> singleRequest = new HttpEntity<>(headers);
                            var singleResponse = restTemplate.exchange(
                                singleQuoteUrl,
                                HttpMethod.GET,
                                singleRequest,
                                QuoteDTO[].class
                            );
                            
                            QuoteDTO[] singleQuotes = singleResponse.getBody();
                            if (singleQuotes != null && singleQuotes.length > 0) {
                                allQuotes.addAll(Arrays.asList(singleQuotes));
                                log.info("Successfully retrieved 1 quote individually (total so far: {})", allQuotes.size());
                                offset += 1;
                                pageNumber++;
                                consecutiveFailures = 0;
                                continue;
                            } else {
                                hasMore = false;
                                log.debug("No quotes returned with pageSize=1, reached end");
                                break;
                            }
                        } catch (Exception singleException) {
                            skippedQuotes++;
                            log.error("Even single quote fetch failed at offset {}: {}. Skipping this quote (too large, likely >10MB attachment) and continuing. Total skipped: {}", 
                                    offset, singleException.getMessage(), skippedQuotes);
                            consecutiveFailures++;
                            offset += 1;
                            pageNumber++;
                            
                            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                                log.error("Too many consecutive failures ({}). Stopping pagination to avoid infinite loop.", 
                                        consecutiveFailures);
                                hasMore = false;
                                break;
                            }
                        }
                    } else {
                        log.error("Error fetching page {}: {}. Skipping this page.", pageNumber + 1, e.getMessage());
                        consecutiveFailures++;
                        offset += pageSize;
                        pageNumber++;
                        
                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            log.error("Too many consecutive failures ({}). Stopping pagination.", consecutiveFailures);
                            hasMore = false;
                            break;
                        }
                    }
                } catch (Exception e) {
                    String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    boolean isContentLengthError = errorMessage.contains("contentlength") || 
                                                   errorMessage.contains("content length") ||
                                                   errorMessage.contains("exceeded");
                    
                    if (isContentLengthError && pageSize > 1) {
                        log.warn("Content length error at page {}: {}. Trying with pageSize=1.", 
                                pageNumber + 1, e.getMessage());
                        
                        try {
                            UriComponentsBuilder singleBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                                .queryParam("limit", 1)
                                .queryParam("offset", offset);
                            
                            if (queryParams != null) {
                                queryParams.forEach(singleBuilder::queryParam);
                            }
                            
                            String singleQuoteUrl = singleBuilder.build(true).toUriString();
                            
                            HttpEntity<?> singleRequest = new HttpEntity<>(headers);
                            var singleResponse = restTemplate.exchange(
                                singleQuoteUrl,
                                HttpMethod.GET,
                                singleRequest,
                                QuoteDTO[].class
                            );
                            
                            QuoteDTO[] singleQuotes = singleResponse.getBody();
                            if (singleQuotes != null && singleQuotes.length > 0) {
                                allQuotes.addAll(Arrays.asList(singleQuotes));
                                offset += 1;
                                pageNumber++;
                                consecutiveFailures = 0;
                                continue;
                            }
                        } catch (Exception singleException) {
                            skippedQuotes++;
                            log.error("Single quote fetch also failed: {}. Skipping quote at offset {} (too large, likely >10MB attachment). Total skipped: {}", 
                                    singleException.getMessage(), offset, skippedQuotes);
                            offset += 1;
                            pageNumber++;
                            consecutiveFailures++;
                        }
                    } else {
                        log.error("Unexpected error fetching page {}: {}. Skipping this page.", 
                                pageNumber + 1, e.getMessage(), e);
                        consecutiveFailures++;
                        offset += pageSize;
                        pageNumber++;
                    }
                    
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        log.error("Too many consecutive failures ({}). Stopping pagination.", consecutiveFailures);
                        hasMore = false;
                        break;
                    }
                }
            }
            
            if (skippedQuotes > 0) {
                log.warn("Retrieved {} quotes total using pagination ({} pages), but {} quotes were skipped due to size exceeding 10MB limit (likely due to large attachments)", 
                        allQuotes.size(), pageNumber + 1, skippedQuotes);
            } else {
                log.info("Successfully retrieved {} quotes total using pagination ({} pages)", allQuotes.size(), pageNumber + 1);
            }
            return allQuotes;
            
        } catch (Exception e) {
            log.error("Error in pagination helper: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Wait until the attachment is actually persisted by the TMForum API
     * The TMForum API might return success before async processing completes
     * This method will block until the attachment is confirmed or timeout occurs
     */
    private void waitForAttachmentPersistence(String quoteId, String fileName, int maxAttempts, int delayMs) {
        for (int i = 0; i < maxAttempts; i++) {
            try {
                // Wait before checking (except for first attempt)
                if (i > 0) {
                    Thread.sleep(delayMs);
                }
                
                // Fetch the quote again to verify attachment exists
                Optional<QuoteDTO> verificationQuoteOpt = findById(quoteId);
                if (verificationQuoteOpt.isPresent()) {
                    QuoteDTO verificationQuote = verificationQuoteOpt.get();
                    
                    // Check if quote items have attachments
                    if (verificationQuote.getQuoteItem() != null) {
                        for (QuoteItemDTO quoteItem : verificationQuote.getQuoteItem()) {
                            if (quoteItem.getAttachment() != null) {
                                for (AttachmentRefOrValueDTO attachment : quoteItem.getAttachment()) {
                                    if (attachment.getName() != null && attachment.getName().equals(fileName)) {
                                        // Additional verification: check if attachment has content
                                        boolean hasContent = attachment.getContent() != null && !attachment.getContent().isEmpty();
                                        boolean hasSize = attachment.getSize() != null && attachment.getSize().getAmount() > 0;
                                        
                                        if (hasContent && hasSize) {
                                            log.info("Attachment confirmed as persisted on attempt {}: {}", i + 1, fileName);
                                            return; // Success - attachment found with content
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                log.warn("Attachment not yet persisted, attempt {} of {}: {}", i + 1, maxAttempts, fileName);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Attachment persistence check interrupted", e);
                throw new RuntimeException("File upload verification was interrupted", e);
            } catch (Exception e) {
                log.error("Error during attachment persistence check attempt {}: {}", i + 1, e.getMessage());
            }
        }
        
        // If we get here, attachment was not found after all attempts
        log.error("Attachment was not persisted after {} attempts and {} seconds total wait time for file: {}", 
                maxAttempts, (maxAttempts * delayMs) / 1000, fileName);
        throw new RuntimeException("File upload failed: attachment was not persisted within timeout period. Please try again.");
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