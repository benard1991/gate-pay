package com.gatepay.notificationservice.service;

import com.gatepay.notificationservice.dto.NotificationMessage;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationHandlerService {

    private final Map<String, NotificationStrategy> strategyMap;

    public NotificationHandlerService(Map<String, NotificationStrategy> strategyMap) {
        this.strategyMap = strategyMap;
    }

    public void handle(NotificationMessage message) {
        NotificationStrategy strategy = strategyMap.get(message.getType().toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported notification type: " + message.getType());
        }
        strategy.send(message);
    }
}
