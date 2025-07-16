package com.dome.quotemanagement.service;

import com.dome.quotemanagement.config.AppConfig;
import com.dome.quotemanagement.dto.NotificationRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final RestTemplate restTemplate;
    private final AppConfig appConfig;

    @Value("${notification.api.base-url}")
    private String notificationBaseUrl;

    @Override
    public void sendNotification(NotificationRequestDTO notification) {
        String url = notificationBaseUrl + appConfig.getNotificationEndpoint();
        
        log.info("Sending notification to URL: {}", url);
        log.debug("Notification payload: {}", notification);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<NotificationRequestDTO> request = new HttpEntity<>(notification, headers);

            restTemplate.postForObject(url, request, Void.class);
            
            log.info("Successfully sent notification for seller: '{}', customer: '{}'", 
                    notification.getSender(), notification.getRecipient());
                    
        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage(), e);
            // We don't throw the exception to prevent notification failures from affecting the main flow
        }
    }
} 