package com.dome.quotemanagement.service;

import com.dome.quotemanagement.dto.tmforum.QuoteDTO;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Optional;

public interface QuoteService {
    List<QuoteDTO> findAllQuotes();
    List<QuoteDTO> findQuotesByUser(String userId, String role);
    List<QuoteDTO> findTenderingQuotesByUser(String userId, String role, String externalId);
    List<QuoteDTO> findCoordinatorQuotesByUser(String userId);
    Optional<QuoteDTO> findById(String id);
    QuoteDTO create(String buyerMessage, String buyerIdRef, String providerIdRef, String productOfferingId);
    QuoteDTO createTenderingQuote(String buyerMessage, String buyerIdRef, String providerIdRef, String externalId);
    QuoteDTO createCoordinatorQuote(String buyerMessage, String buyerIdRef);
    Optional<QuoteDTO> updateQuoteStatus(String quoteId, String statusValue);
    Optional<QuoteDTO> updateQuoteNote(String quoteId, String userId, String messageContent);
    Optional<QuoteDTO> updateQuoteAttachment(String quoteId, MultipartFile file, String description);
    Optional<QuoteDTO> updateQuoteDate(String quoteId, String date, String dateType);
    void delete(String id);
} 