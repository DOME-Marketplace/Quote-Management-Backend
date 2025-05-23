package com.dome.quotemanagement.service;

import com.dome.quotemanagement.dto.tmforum.QuoteDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteServiceImpl implements QuoteService {
    
    private final RestTemplate restTemplate;
    
    @Value("${tmforum.api.base-url}")
    private String tmforumBaseUrl;
    
    @Override
    public List<QuoteDTO> findAll() {
        String url = tmforumBaseUrl + "/quoteManagement/v4/quote";
        log.debug("Calling external TMForum API: {}", url);
        try {
            QuoteDTO[] quotes = restTemplate.getForObject(url, QuoteDTO[].class);
            return Arrays.asList(quotes != null ? quotes : new QuoteDTO[0]);
        } catch (Exception e) {
            log.error("Error calling TMForum API: {}", e.getMessage());
            return Arrays.asList();
        }
    }
    
    @Override
    public Optional<QuoteDTO> findById(String id) {
        String url = tmforumBaseUrl + "/quoteManagement/v4/quote/" + id;
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
    public QuoteDTO create(QuoteDTO quoteDTO) {
        String url = tmforumBaseUrl + "/quoteManagement/v4/quote";
        log.debug("Calling external TMForum API: {}", url);
        try {
            return restTemplate.postForObject(url, quoteDTO, QuoteDTO.class);
        } catch (Exception e) {
            log.error("Error calling TMForum API: {}", e.getMessage());
            throw new RuntimeException("Failed to create quote", e);
        }
    }
    
    @Override
    public Optional<QuoteDTO> update(String id, QuoteDTO quoteDTO) {
        String url = tmforumBaseUrl + "/quoteManagement/v4/quote/" + id;
        log.debug("Calling external TMForum API: {}", url);
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<QuoteDTO> requestEntity = new HttpEntity<>(quoteDTO, headers);
            QuoteDTO updatedQuote = restTemplate.exchange(
                url, 
                HttpMethod.PATCH, 
                requestEntity, 
                QuoteDTO.class
            ).getBody();
            return Optional.ofNullable(updatedQuote);
        } catch (Exception e) {
            log.error("Error calling TMForum API: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    @Override
    public void delete(String id) {
        String url = tmforumBaseUrl + "/quoteManagement/v4/quote/" + id;
        log.debug("Calling external TMForum API: {}", url);
        try {
            restTemplate.delete(url);
        } catch (Exception e) {
            log.error("Error calling TMForum API: {}", e.getMessage());
            throw new RuntimeException("Failed to delete quote", e);
        }
    }
    
    @Override
    public List<QuoteDTO> findByState(String state) {
        String url = UriComponentsBuilder.fromHttpUrl(tmforumBaseUrl + "/quoteManagement/v4/quote")
                .queryParam("state", state)
                .build()
                .toString();
        log.debug("Calling external TMForum API: {}", url);
        try {
            QuoteDTO[] quotes = restTemplate.getForObject(url, QuoteDTO[].class);
            return Arrays.asList(quotes != null ? quotes : new QuoteDTO[0]);
        } catch (Exception e) {
            log.error("Error calling TMForum API: {}", e.getMessage());
            return Arrays.asList();
        }
    }
    
    @Override
    public Optional<QuoteDTO> findByExternalId(String externalId) {
        String url = UriComponentsBuilder.fromHttpUrl(tmforumBaseUrl + "/quoteManagement/v4/quote")
                .queryParam("externalId", externalId)
                .build()
                .toString();
        log.debug("Calling external TMForum API: {}", url);
        try {
            QuoteDTO[] quotes = restTemplate.getForObject(url, QuoteDTO[].class);
            return quotes != null && quotes.length > 0 ? Optional.of(quotes[0]) : Optional.empty();
        } catch (Exception e) {
            log.error("Error calling TMForum API: {}", e.getMessage());
            return Optional.empty();
        }
    }
} 