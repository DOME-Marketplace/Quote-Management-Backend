package com.dome.quotemanagement.service;

import com.dome.quotemanagement.dto.tmforum.QuoteDTO;
import java.util.List;
import java.util.Optional;

public interface QuoteService {
    List<QuoteDTO> findAll();
    Optional<QuoteDTO> findById(String id);
    QuoteDTO create(QuoteDTO quoteDTO);
    Optional<QuoteDTO> update(String id, QuoteDTO quoteDTO);
    void delete(String id);
    List<QuoteDTO> findByState(String state);
    Optional<QuoteDTO> findByExternalId(String externalId);
} 