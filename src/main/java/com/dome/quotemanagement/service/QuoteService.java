package com.dome.quotemanagement.service;

import com.dome.quotemanagement.dto.tmforum.AttachmentRefOrValueDTO;
import com.dome.quotemanagement.dto.tmforum.QuoteDTO;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Optional;

public interface QuoteService {
    List<QuoteDTO> findAllQuotes();
    List<QuoteDTO> findQuotesByUser(String customerId);
    Optional<QuoteDTO> findById(String id);
    QuoteDTO create(String customerMessage, String customerIdRef, String providerIdRef);
    Optional<QuoteDTO> updateQuoteStatus(String quoteId, String statusValue);
    Optional<QuoteDTO> updateQuoteNote(String quoteId, String userId, String messageContent);
    Optional<QuoteDTO> updateQuoteAttachment(String quoteId, MultipartFile file, String description);
    Optional<QuoteDTO> updateQuoteDate(String quoteId, String date);
    void delete(String id);
} 