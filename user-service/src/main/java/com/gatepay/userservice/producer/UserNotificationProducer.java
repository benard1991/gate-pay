package com.gatepay.userservice.producer;

import com.gatepay.userservice.dto.NotificationMessage;
import lombok.AllArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@AllArgsConstructor
public class UserNotificationProducer {
    private final RabbitTemplate rabbitTemplate;



    public void sendOtpEmail(String to, String otp) {
        String subject = "Password Reset OTP";

        String body = String.format(
                """
                        Hello,

                        You requested a password reset. Your OTP code is: %s

                        This code will expire in 10 minutes. If you did not request a password reset, please ignore this email.""",
                otp
        );

        NotificationMessage message = new NotificationMessage(
                to,
                "EMAIL",
                subject,
                body,
                Collections.emptyMap()
        );

        rabbitTemplate.convertAndSend("notification-exchange", "notification-routingKey", message);
    }



    public void sendResetPasswordEmail(String to) {
        String subject = "Reset Your Password";
        String body = "You have requested to reset your password. " +
                "Please use the OTP sent to your email or follow the instructions to reset your password.";

        NotificationMessage message = new NotificationMessage(
                to,
                "EMAIL",
                subject,
                body,
                Collections.emptyMap()
        );

        rabbitTemplate.convertAndSend("notification-exchange", "notification-routingKey", message);
    }

}
