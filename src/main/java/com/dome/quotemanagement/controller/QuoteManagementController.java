package com.dome.quotemanagement.controller;

import com.dome.quotemanagement.dto.QuoteCreateRequestDTO;
import com.dome.quotemanagement.dto.tmforum.AttachmentRefOrValueDTO;
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
        return ResponseEntity.ok(quoteService.findAllQuotes());
    }

    @GetMapping("/quoteByUser/{customerId}")
    @Operation(
        summary = "List quotes by user", 
        description = "Retrieves a list of quotes related to a specific customer. Backend calls: /quote?limit=100 with filtering"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved user quotes",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid customer ID"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<QuoteDTO>> listQuotesByUser(
            @Parameter(description = "Customer ID to filter quotes by", required = true)
            @PathVariable String customerId) {
        if (customerId == null || customerId.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(quoteService.findQuotesByUser(customerId));
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
        return quoteService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/createQuote")
    @Operation(
        summary = "Create a new quote", 
        description = "Creates a new quote with customer message and related parties. Backend calls: /quote"
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
        QuoteDTO createdQuote = quoteService.create(
            request.getCustomerMessage(), 
            request.getCustomerIdRef(), 
            request.getProviderIdRef()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(createdQuote);
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
        return quoteService.updateQuoteStatus(id, statusValue)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
        return quoteService.updateQuoteNote(id, userId, messageContent)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping(value = "/addAttachmentToQuote/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Add attachment to quote", 
        description = "Uploads and adds a PDF file attachment to the quote. The file content is embedded as base64 in the attachment object. Accepts multipart file upload. Backend calls: /quote/{id}"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quote attachment added successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "404", description = "Quote not found"),
        @ApiResponse(responseCode = "400", description = "Invalid file or file format (only PDF allowed)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<QuoteDTO> updateQuoteAttachment(
            @Parameter(description = "Quote ID", required = true)
            @PathVariable String id,
            @Parameter(description = "PDF file to attach", required = true, 
                      content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional description for the attachment")
            @RequestParam(value = "description", required = false) String description) {
        
        try {
            return quoteService.updateQuoteAttachment(id, file, description)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PatchMapping("/updateQuoteDate/{id}")
    @Operation(
        summary = "Update quote completion date", 
        description = "Updates the requested completion date for the quote. Date should be in DD-MM-YYYY format (e.g., 31-12-2024). The time will be automatically set to end of day. Backend calls: /quote/{id}"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Quote date updated successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = QuoteDTO.class))),
        @ApiResponse(responseCode = "404", description = "Quote not found"),
        @ApiResponse(responseCode = "400", description = "Invalid date format (expected DD-MM-YYYY)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<QuoteDTO> updateQuoteDate(
            @Parameter(description = "Quote ID", required = true)
            @PathVariable String id,
            @Parameter(description = "New completion date in DD-MM-YYYY format (e.g., 31-12-2024)", required = true)
            @RequestParam String date) {
        return quoteService.updateQuoteDate(id, date)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
        quoteService.delete(id);
        return ResponseEntity.noContent().build();
    }
} 