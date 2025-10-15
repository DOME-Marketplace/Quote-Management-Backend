package com.dome.quotemanagement.controller;

import com.dome.quotemanagement.dto.QuoteCreateRequestDTO;
import com.dome.quotemanagement.dto.TenderingQuoteCreateRequestDTO;
import com.dome.quotemanagement.dto.CoordinatorQuoteCreateRequestDTO;
import com.dome.quotemanagement.dto.tmforum.QuoteDTO;
import com.dome.quotemanagement.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/quoteManagement")
@RequiredArgsConstructor
@Tag(name = "Quote Management", description = "API for managing quotes in the DOME Marketplace")
@Slf4j
public class QuoteManagementController {

    private final QuoteService quoteService;

    @GetMapping("/listAllQuotes")
    @Operation(
        summary = "List all quotes", 
        description = "Retrieves a list of all quotes from all users. Backend calls: /quote?limit=100"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved all quotes",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<QuoteDTO>> listAllQuotes() {
        log.info("Received request to list all quotes");
        try {
            List<QuoteDTO> quotes = quoteService.findAllQuotes();
            log.info("Successfully retrieved {} quotes", quotes.size());
            return ResponseEntity.ok(quotes);
        } catch (Exception e) {
            log.error("Error listing all quotes: {}", e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/quoteByUser/{userId}")
    @Operation(
        summary = "List tailored quotes by user", 
        description = "Retrieves a list of tailored quotes related to a specific user and role. The role determines where to look for the user ID: " +
                     "- If role is 'Customer', looks for the ID in Quote.QuoteItem.RelatedParty " +
                     "- If role is 'Seller', looks for the ID in Quote.RelatedParty " +
                     "This endpoint only returns quotes with category='tailored'. For tendering quotes, use /tendering/quotes/{userId}"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved user quotes",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid user ID or role"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<QuoteDTO>> listQuotesByUser(
            @Parameter(description = "User ID to filter quotes by", required = true)
            @PathVariable String userId,
            @Parameter(description = "Role to filter quotes by ('Customer' or 'Seller')", required = true)
            @RequestParam String role) {
        log.info("Received request to list tailored quotes for user: '{}' with role: '{}'", userId, role);
        
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("Invalid user ID provided: '{}'", userId);
            return ResponseEntity.badRequest().build();
        }
        
        if (role == null || role.trim().isEmpty()) {
            log.warn("Invalid role provided: '{}'", role);
            return ResponseEntity.badRequest().build();
        }
        
        try {
            List<QuoteDTO> quotes = quoteService.findQuotesByUser(userId, role);
            log.info("Successfully retrieved {} tailored quotes for user: '{}' with role: '{}'", quotes.size(), userId, role);
            return ResponseEntity.ok(quotes);
        } catch (Exception e) {
            log.error("Error listing tailored quotes for user '{}' with role '{}': {}", userId, role, e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/tendering/quotes/{userId}")
    @Operation(
        summary = "List tendering quotes by user and external ID", 
        description = "Retrieves all tendering quotes for a specific user within a tendering process (same externalId). " +
                     "This endpoint filters quotes by: userId + role + category='tender' + externalId. " +
                     "Used to get all quotes related to the same tendering process."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved tendering quotes",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid user ID, role, or external ID"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<QuoteDTO>> listTenderingQuotesByUser(
            @Parameter(description = "User ID to filter quotes by", required = true)
            @PathVariable String userId,
            @Parameter(description = "Role to filter quotes by ('Customer' or 'Seller')", required = true)
            @RequestParam String role,
            @Parameter(description = "External ID to group tendering quotes by the same process", required = true)
            @RequestParam String externalId) {
        log.info("Received request to list tendering quotes for user: '{}' with role: '{}' and externalId: '{}'", userId, role, externalId);
        
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("Invalid user ID provided: '{}'", userId);
            return ResponseEntity.badRequest().build();
        }
        
        if (role == null || role.trim().isEmpty()) {
            log.warn("Invalid role provided: '{}'", role);
            return ResponseEntity.badRequest().build();
        }
        
        if (externalId == null || externalId.trim().isEmpty()) {
            log.warn("Invalid external ID provided: '{}'", externalId);
            return ResponseEntity.badRequest().build();
        }
        
        try {
            List<QuoteDTO> quotes = quoteService.findTenderingQuotesByUser(userId, role, externalId);
            log.info("Successfully retrieved {} tendering quotes for user: '{}' with role: '{}' and externalId: '{}'", quotes.size(), userId, role, externalId);
            return ResponseEntity.ok(quotes);
        } catch (Exception e) {
            log.error("Error listing tendering quotes for user '{}' with role '{}' and externalId '{}': {}", userId, role, externalId, e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/tendering/coordinatorQuotes/{userId}")
    @Operation(
        summary = "List coordinator quotes by user", 
        description = "Retrieves all coordinator (shadow) quotes for a specific user. " +
                     "This endpoint filters quotes by: userId + category='coordinator'. " +
                     "Coordinator quotes are used for managing tendering processes and are not displayed in regular user GUIs."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved coordinator quotes",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid user ID"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<QuoteDTO>> listCoordinatorQuotesByUser(
            @Parameter(description = "User ID to filter quotes by", required = true)
            @PathVariable String userId) {
        log.info("Received request to list coordinator quotes for user: '{}'", userId);
        
        if (userId == null || userId.trim().isEmpty()) {
            log.warn("Invalid user ID provided: '{}'", userId);
            return ResponseEntity.badRequest().build();
        }
        
        try {
            List<QuoteDTO> quotes = quoteService.findCoordinatorQuotesByUser(userId);
            log.info("Successfully retrieved {} coordinator quotes for user: '{}'", quotes.size(), userId);
            return ResponseEntity.ok(quotes);
        } catch (Exception e) {
            log.error("Error listing coordinator quotes for user '{}': {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/quoteById/{id}")
    @Operation(
        summary = "Get quote by ID", 
        description = "Retrieves a specific quote by its ID. Backend calls: /quote/{id}"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved quote",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "404", description = "Quote not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<QuoteDTO> getQuote(
            @Parameter(description = "Quote ID", required = true)
            @PathVariable String id) {
        log.info("Received request to get quote by ID: '{}'", id);
        
        try {
            return quoteService.findById(id)
                    .map(quote -> {
                        log.info("Successfully retrieved quote with ID: '{}'", id);
                        return ResponseEntity.ok(quote);
                    })
                    .orElseGet(() -> {
                        log.warn("Quote not found with ID: '{}'", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (Exception e) {
            log.error("Error retrieving quote with ID '{}': {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/createQuote")
    @Operation(
        summary = "Create a new tailored quote", 
        description = "Creates a new tailored quote with customer message and related parties. " +
                     "The category is automatically set to 'tailored' and externalId is set to '0000' (not significant for tailored quotes). " +
                     "For tendering quotes, use /tendering/createQuote endpoint. Backend calls: /quote"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Quote created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<QuoteDTO> createQuote(
            @Parameter(description = "Quote creation request", required = true)
            @Valid @RequestBody QuoteCreateRequestDTO request) {
        log.info("Received request to create tailored quote with payload: customerMessage='{}', customerIdRef='{}', providerIdRef='{}', productOfferingId='{}'",
                request.getCustomerMessage(), request.getCustomerIdRef(), request.getProviderIdRef(), request.getProductOfferingId());
        
        try {
            QuoteDTO createdQuote = quoteService.create(
                request.getCustomerMessage(), 
                request.getCustomerIdRef(), 
                request.getProviderIdRef(),
                request.getProductOfferingId()
            );
            log.info("Successfully created quote with ID: '{}'", createdQuote.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(createdQuote);
        } catch (Exception e) {
            log.error("Error creating quote with payload: {}", request, e);
            throw e;
        }
    }

    @PostMapping("/tendering/createQuote")
    @Operation(
        summary = "Create a new tendering quote", 
        description = "Creates a new tendering quote with customer message, related parties, and externalId for grouping. " +
                     "The category is automatically set to 'tender'. The externalId is required to group related quotes in the same tendering process. " +
                     "Backend calls: /quote"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Tendering quote created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<QuoteDTO> createTenderingQuote(
            @Parameter(description = "Tendering quote creation request", required = true)
            @Valid @RequestBody TenderingQuoteCreateRequestDTO request) {
        log.info("Received request to create tendering quote with payload: customerMessage='{}', customerIdRef='{}', providerIdRef='{}', externalId='{}'",
                request.getCustomerMessage(), request.getCustomerIdRef(), request.getProviderIdRef(), request.getExternalId());
        
        try {
            QuoteDTO createdQuote = quoteService.createTenderingQuote(
                request.getCustomerMessage(), 
                request.getCustomerIdRef(), 
                request.getProviderIdRef(),
                request.getExternalId()
            );
            log.info("Successfully created tendering quote with ID: '{}'", createdQuote.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(createdQuote);
        } catch (Exception e) {
            log.error("Error creating tendering quote with payload: {}", request, e);
            throw e;
        }
    }

    @PostMapping("/tendering/createCoordinatorQuote")
    @Operation(
        summary = "Create a new coordinator quote", 
        description = "Creates a new coordinator (shadow) quote for managing a tendering process. " +
                     "This quote serves as a master record to store process-level information and is not displayed in regular user quote lists. " +
                     "The category is automatically set to 'coordinator'. Only requires customer information and a description/title. " +
                     "Backend calls: /quote"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Coordinator quote created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<QuoteDTO> createCoordinatorQuote(
            @Parameter(description = "Coordinator quote creation request", required = true)
            @Valid @RequestBody CoordinatorQuoteCreateRequestDTO request) {
        log.info("Received request to create coordinator quote with payload: customerMessage='{}', customerIdRef='{}'",
                request.getCustomerMessage(), request.getCustomerIdRef());
        
        try {
            QuoteDTO createdQuote = quoteService.createCoordinatorQuote(
                request.getCustomerMessage(), 
                request.getCustomerIdRef()
            );
            log.info("Successfully created coordinator quote with ID: '{}'", createdQuote.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(createdQuote);
        } catch (Exception e) {
            log.error("Error creating coordinator quote with payload: {}", request, e);
            throw e;
        }
    }

    @PatchMapping("/updateQuoteStatus/{id}")
    @Operation(
        summary = "Update quote status", 
        description = "Updates the status/state of a specific quote. Valid states: inProgress, pending, approved, cancelled, accepted, rejected. Backend calls: /quote/{id}"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quote status updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "404", description = "Quote not found"),
        @ApiResponse(responseCode = "400", description = "Invalid status value"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<QuoteDTO> updateQuoteStatus(
            @Parameter(description = "Quote ID", required = true)
            @PathVariable String id,
            @Parameter(description = "New status value (inProgress, pending, approved, cancelled, accepted, rejected)", required = true)
            @RequestParam String statusValue) {
        log.info("Received request to update quote status - quoteId: '{}', statusValue: '{}'", id, statusValue);
        
        try {
            return quoteService.updateQuoteStatus(id, statusValue)
                    .map(quote -> {
                        log.info("Successfully updated quote status - quoteId: '{}', statusValue: '{}'", id, statusValue);
                        return ResponseEntity.ok(quote);
                    })
                    .orElseGet(() -> {
                        log.warn("Quote not found for status update - quoteId: '{}'", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (Exception e) {
            log.error("Error updating quote status - quoteId: '{}', statusValue: '{}': {}", id, statusValue, e.getMessage(), e);
            throw e;
        }
    }

    @PatchMapping("/addNoteToQuote/{id}")
    @Operation(
        summary = "Add note to quote", 
        description = "Adds a new note/message to the quote conversation history. Backend calls: /quote/{id}"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quote note added successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "404", description = "Quote not found"),
        @ApiResponse(responseCode = "400", description = "Invalid message content or user ID"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<QuoteDTO> updateQuoteNote(
            @Parameter(description = "Quote ID", required = true)
            @PathVariable String id,
            @Parameter(description = "User ID of the person adding the note", required = true)
            @RequestParam String userId,
            @Parameter(description = "Note/message content to add", required = true)
            @RequestParam String messageContent) {
        log.info("Received request to add note to quote - quoteId: '{}', userId: '{}', messageContent: '{}'", id, userId, messageContent);
        
        try {
            return quoteService.updateQuoteNote(id, userId, messageContent)
                    .map(quote -> {
                        log.info("Successfully added note to quote - quoteId: '{}', userId: '{}'", id, userId);
                        return ResponseEntity.ok(quote);
                    })
                    .orElseGet(() -> {
                        log.warn("Quote not found for note update - quoteId: '{}'", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (Exception e) {
            log.error("Error adding note to quote - quoteId: '{}', userId: '{}': {}", id, userId, e.getMessage(), e);
            throw e;
        }
    }

    @PatchMapping(value = "/addAttachmentToQuote/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Add attachment to quote", 
        description = "Adds a PDF attachment to the quote. Only PDF files up to 100MB are allowed. Backend calls: /quote/{id}"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quote attachment added successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "404", description = "Quote not found"),
        @ApiResponse(responseCode = "400", description = "Invalid file or description"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<QuoteDTO> updateQuoteAttachment(
            @Parameter(description = "Quote ID", required = true)
            @PathVariable String id,
            @Parameter(description = "PDF file to attach (max 100MB)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Description of the attachment", required = false)
            @RequestParam(value = "description", required = false, defaultValue = "") String description) {
        log.info("Received request to add attachment to quote - quoteId: '{}', filename: '{}', size: {} bytes, description: '{}'", 
                id, file.getOriginalFilename(), file.getSize(), description);
        
        try {
            return quoteService.updateQuoteAttachment(id, file, description)
                    .map(quote -> {
                        log.info("Successfully added attachment to quote - quoteId: '{}', filename: '{}'", id, file.getOriginalFilename());
                        return ResponseEntity.ok(quote);
                    })
                    .orElseGet(() -> {
                        log.warn("Quote not found for attachment upload - quoteId: '{}'", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (IllegalArgumentException e) {
            log.warn("Validation error for attachment upload - quoteId: '{}', filename: '{}': {}", id, file.getOriginalFilename(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error adding attachment to quote - quoteId: '{}', filename: '{}': {}", id, file.getOriginalFilename(), e.getMessage(), e);
            throw e;
        }
    }

    @PatchMapping("/updateQuoteDate/{id}")
    @Operation(
        summary = "Update quote date", 
        description = "Updates various date fields for a quote. Supported dateType values: 'requested' (requestedQuoteCompletionDate), 'expected' (expectedQuoteCompletionDate), 'effective' (effectiveQuoteCompletionDate), 'expectedFulfillment' (expectedFulfillmentStartDate). Date format: DD-MM-YYYY. Backend calls: /quote/{id}"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quote date updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "404", description = "Quote not found"),
        @ApiResponse(responseCode = "400", description = "Invalid date format or dateType"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<QuoteDTO> updateQuoteDate(
            @Parameter(description = "Quote ID", required = true)
            @PathVariable String id,
            @Parameter(description = "Completion date in DD-MM-YYYY format (e.g., 31-12-2024)", required = true)
            @RequestParam String date,
            @Parameter(description = "Type of date to update: 'requested', 'expected', 'effective', or 'expectedFulfillment'", required = true)
            @RequestParam String dateType)
    {
        log.info("Received request to update quote date - quoteId: '{}', date: '{}', dateType: '{}'", id, date, dateType);
        
        try {
            return quoteService.updateQuoteDate(id, date, dateType)
                    .map(quote -> {
                        log.info("Successfully updated quote date - quoteId: '{}', date: '{}', dateType: '{}'", id, date, dateType);
                        return ResponseEntity.ok(quote);
                    })
                    .orElseGet(() -> {
                        log.warn("Quote not found for date update - quoteId: '{}'", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (IllegalArgumentException e) {
            log.warn("Invalid date format or dateType for quote update - quoteId: '{}', date: '{}', dateType: '{}': {}", id, date, dateType, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error updating quote date - quoteId: '{}', date: '{}', dateType: '{}': {}", id, date, dateType, e.getMessage(), e);
            throw e;
        }
    }

    @DeleteMapping("/quote/{id}")
    @Operation(
        summary = "Delete quote", 
        description = "Deletes a specific quote by its ID. Backend calls: /quote/{id}"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Quote deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Quote not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteQuote(
            @Parameter(description = "Quote ID", required = true)
            @PathVariable String id) {
        log.info("Received request to delete quote - quoteId: '{}'", id);
        
        try {
            quoteService.delete(id);
            log.info("Successfully deleted quote - quoteId: '{}'", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error deleting quote - quoteId: '{}': {}", id, e.getMessage(), e);
            throw e;
        }
    }
} 