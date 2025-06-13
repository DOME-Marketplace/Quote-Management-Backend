package com.dome.quotemanagement.service;

import com.dome.quotemanagement.dto.NotificationRequestDTO;

public interface NotificationService {
    void sendNotification(NotificationRequestDTO notification);
} 