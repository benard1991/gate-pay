package com.gatepay.notificationservice.strategy;

import com.gatepay.notificationservice.dto.NotificationMessage;
import com.gatepay.notificationservice.service.NotificationStrategy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component("EMAIL")
@RequiredArgsConstructor
public class EmailStrategy implements NotificationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(EmailStrategy.class);
    private final JavaMailSender mailSender;


    @Override
    public void send(NotificationMessage message) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(message.getRecipient());
            mailMessage.setSubject(message.getSubject());
            mailMessage.setText(message.getBody());
            mailSender.send(mailMessage);

            logger.info("Email sent to {} successfully", message.getRecipient());
        } catch (Exception e) {
            logger.error("Failed to send email to {}", message.getRecipient(), e);
        }
    }
}
