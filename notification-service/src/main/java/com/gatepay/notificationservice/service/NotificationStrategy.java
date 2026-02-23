package com.gatepay.notificationservice.service;

import com.gatepay.notificationservice.dto.NotificationMessage;

public interface NotificationStrategy {
    void send(NotificationMessage message);
}
