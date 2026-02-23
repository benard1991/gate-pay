package com.gatepay.notificationservice.listener;

import com.gatepay.notificationservice.config.RabbitMQConfig;
import com.gatepay.notificationservice.dto.NotificationMessage;
import com.gatepay.notificationservice.service.NotificationHandlerService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(NotificationListener.class);
    private final NotificationHandlerService handlerService;

    /**
     * Listens to notifications from the queue.
     * If processing fails, an exception is thrown to trigger the DLQ/retry mechanism.
     */
    @RabbitListener(
            queues = RabbitMQConfig.NOTIFICATION_QUEUE,
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void listen(NotificationMessage message) {
        try {
            logger.info("Received notification: {}", message);
            handlerService.handle(message);
        } catch (Exception ex) {
            logger.error("Failed to process notification: {}", message, ex);
            // Throwing exception ensures RabbitMQ routes the message to retry queue / DLQ
            throw new RuntimeException("Failed to handle notification", ex);
        }
    }
}
