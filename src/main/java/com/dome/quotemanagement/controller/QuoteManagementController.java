package com.dome.quotemanagement.controller;

import com.dome.quotemanagement.dto.tmforum.QuoteDTO;
import com.dome.quotemanagement.service.QuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/quoteManagement/v4")
@RequiredArgsConstructor
public class QuoteManagementController {

    private final QuoteService quoteService;

    @GetMapping("/quote")
    public ResponseEntity<List<QuoteDTO>> listQuotes(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String externalId) {
        if (state != null) {
            return ResponseEntity.ok(quoteService.findByState(state));
        }
        if (externalId != null) {
            return quoteService.findByExternalId(externalId)
                    .map(quote -> ResponseEntity.ok(List.of(quote)))
                    .orElse(ResponseEntity.notFound().build());
        }
        return ResponseEntity.ok(quoteService.findAll());
    }

    @GetMapping("/quote/{id}")
    public ResponseEntity<QuoteDTO> getQuote(@PathVariable String id) {
        return quoteService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/quote")
    public ResponseEntity<QuoteDTO> createQuote(@RequestBody QuoteDTO quoteDTO) {
        QuoteDTO createdQuote = quoteService.create(quoteDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdQuote);
    }

    @PatchMapping("/quote/{id}")
    public ResponseEntity<QuoteDTO> updateQuote(
            @PathVariable String id,
            @RequestBody QuoteDTO quoteDTO) {
        return quoteService.update(id, quoteDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/quote/{id}")
    public ResponseEntity<Void> deleteQuote(@PathVariable String id) {
        quoteService.delete(id);
        return ResponseEntity.noContent().build();
    }
} 