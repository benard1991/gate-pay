package com.gatepay.notificationservice.service;

import com.gatepay.notificationservice.dto.NotificationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationHandlerServiceTest {

    @Mock
    private NotificationStrategy emailStrategy;

    @Mock
    private NotificationStrategy smsStrategy;

    private NotificationHandlerService notificationHandlerService;

    @BeforeEach
    void setUp() {
        notificationHandlerService = new NotificationHandlerService(Map.of(
                "EMAIL", emailStrategy,
                "SMS", smsStrategy
        ));
    }

    @Test
    void routesAnEmailNotificationToTheEmailStrategy() {
        NotificationMessage message = buildMessage("EMAIL");

        notificationHandlerService.handle(message);

        verify(emailStrategy).send(message);
        verifyNoInteractions(smsStrategy);
    }

    @Test
    void routesAnSmsNotificationToTheSmsStrategy() {
        NotificationMessage message = buildMessage("SMS");

        notificationHandlerService.handle(message);

        verify(smsStrategy).send(message);
        verifyNoInteractions(emailStrategy);
    }

    @Test
    void routingIsNotCaseSensitiveForTheNotificationType() {
        NotificationMessage message = buildMessage("email");

        notificationHandlerService.handle(message);

        verify(emailStrategy).send(message);
    }

    @Test
    void throwsAnExceptionWhenNoStrategyExistsForTheGivenType() {
        NotificationMessage message = buildMessage("PUSH");

        assertThatThrownBy(() -> notificationHandlerService.handle(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported notification type: PUSH");
    }

    @Test
    void throwsAnExceptionWhenTheNotificationTypeIsNull() {
        NotificationMessage message = buildMessage(null);

        assertThatThrownBy(() -> notificationHandlerService.handle(message))
                .isInstanceOf(Exception.class);
    }

    @Test
    void doesNotCallAnyOtherStrategyWhenOneIsMatched() {
        NotificationMessage message = buildMessage("EMAIL");

        notificationHandlerService.handle(message);

        verifyNoInteractions(smsStrategy);
    }

    // --- helpers ---

    private NotificationMessage buildMessage(String type) {
        NotificationMessage message = new NotificationMessage();
        message.setType(type);
        return message;
    }
}