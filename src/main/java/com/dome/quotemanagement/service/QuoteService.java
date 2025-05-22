package com.dome.quotemanagement.service;

import com.dome.quotemanagement.dto.tmforum.QuoteDTO;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class QuoteService {
    
    private final RestTemplate restTemplate;
    
    @Value("${tmforum.api.base-url}")
    private String tmforumBaseUrl;
    
    public List<QuoteDTO> findAll() {
        String url = tmforumBaseUrl + "/quoteManagement/v4/quote";
        QuoteDTO[] quotes = restTemplate.getForObject(url, QuoteDTO[].class);
        return Arrays.asList(quotes);
    }
    
    public Optional<QuoteDTO> findById(String id) {
        String url = tmforumBaseUrl + "/quoteManagement/v4/quote/" + id;
        try {
            QuoteDTO quote = restTemplate.getForObject(url, QuoteDTO.class);
            return Optional.ofNullable(quote);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    public QuoteDTO create(QuoteDTO quoteDTO) {
        String url = tmforumBaseUrl + "/quoteManagement/v4/quote";
        return restTemplate.postForObject(url, quoteDTO, QuoteDTO.class);
    }
    
    public Optional<QuoteDTO> update(String id, QuoteDTO quoteDTO) {
        String url = tmforumBaseUrl + "/quoteManagement/v4/quote/" + id;
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
            return Optional.empty();
        }
    }
    
    public void delete(String id) {
        String url = tmforumBaseUrl + "/quoteManagement/v4/quote/" + id;
        restTemplate.delete(url);
    }
    
    public List<QuoteDTO> findByState(String state) {
        String url = UriComponentsBuilder.fromHttpUrl(tmforumBaseUrl + "/quoteManagement/v4/quote")
                .queryParam("state", state)
                .build()
                .toString();
        QuoteDTO[] quotes = restTemplate.getForObject(url, QuoteDTO[].class);
        return Arrays.asList(quotes);
    }
    
    public Optional<QuoteDTO> findByExternalId(String externalId) {
        String url = UriComponentsBuilder.fromHttpUrl(tmforumBaseUrl + "/quoteManagement/v4/quote")
                .queryParam("externalId", externalId)
                .build()
                .toString();
        try {
            QuoteDTO[] quotes = restTemplate.getForObject(url, QuoteDTO[].class);
            return quotes != null && quotes.length > 0 ? Optional.of(quotes[0]) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
} 