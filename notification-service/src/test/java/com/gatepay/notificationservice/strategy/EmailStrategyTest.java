package com.gatepay.notificationservice.strategy;

import com.gatepay.notificationservice.dto.NotificationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailStrategyTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailStrategy emailStrategy;

    @BeforeEach
    void setUp() {
        emailStrategy = new EmailStrategy(mailSender);
    }

    @Test
    void sendsAnEmailWithTheRecipientFromTheNotificationMessage() {
        emailStrategy.send(buildMessage("user@gatepay.com", "Welcome", "Hello there"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        assertThat(captor.getValue().getTo()).containsExactly("user@gatepay.com");
    }

    @Test
    void sendsAnEmailWithTheSubjectFromTheNotificationMessage() {
        emailStrategy.send(buildMessage("user@gatepay.com", "Your transfer is complete", "Hello there"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        assertThat(captor.getValue().getSubject()).isEqualTo("Your transfer is complete");
    }

    @Test
    void sendsAnEmailWithTheBodyFromTheNotificationMessage() {
        emailStrategy.send(buildMessage("user@gatepay.com", "Welcome", "Your account is ready"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        assertThat(captor.getValue().getText()).isEqualTo("Your account is ready");
    }

    @Test
    void delegatesToMailSenderExactlyOncePerMessage() {
        emailStrategy.send(buildMessage("user@gatepay.com", "Subject", "Body"));

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void doesNotPropagateAnExceptionWhenMailSenderFails() {
        doThrow(new MailSendException("SMTP server unreachable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // The silent catch means this should never throw — but that's a design risk (see note below)
        assertThatNoException().isThrownBy(() ->
                emailStrategy.send(buildMessage("user@gatepay.com", "Subject", "Body"))
        );
    }

    @Test
    void stillAttemptsToSendEvenWhenSubjectAndBodyAreEmpty() {
        emailStrategy.send(buildMessage("user@gatepay.com", "", ""));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // --- helpers ---

    private NotificationMessage buildMessage(String recipient, String subject, String body) {
        NotificationMessage message = new NotificationMessage();
        message.setRecipient(recipient);
        message.setSubject(subject);
        message.setBody(body);
        return message;
    }
}