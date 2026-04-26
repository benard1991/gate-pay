package com.gatepay.authservice.producer;

import com.gatepay.authservice.dto.NotificationMessage;
import lombok.AllArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@AllArgsConstructor
public class AuthNotificationProducer {

    private final RabbitTemplate rabbitTemplate;


//     Sends OTP email for password reset

    public void sendResetOtpEmail(String to, String otp, String firstName) {
        String subject = "Password Reset OTP";

        String body = String.format(
                "Dear %s,%n%n" +
                        "We received a request to reset your password.%n%n" +
                        "Your One-Time Password (OTP) is:%n" +
                        "%s%n%n" +
                        "This OTP will expire in 10 minutes.%n" +
                        "If you did not request a password reset, please ignore this email or contact our support team.%n%n" +
                        "Best regards,%n" +
                        "GatePay Support Team",
                firstName,
                otp
        );

        NotificationMessage message = new NotificationMessage(to, "EMAIL", subject, body, Collections.emptyMap());

        rabbitTemplate.convertAndSend("notification-exchange", "notification-routingKey", message);
    }


//      Sends OTP email for login (if you need)

    public void sendLoginOtpEmail(String to, String otp,  String firstName) {
        String subject = "Your One-Time Password (OTP) for Login";

        String body = String.format(
                "Dear %s,%n%n" +
                        "We received a request to sign in to your account.%n%n" +
                        "Please use the One-Time Password (OTP) below to complete your login:%n%n" +
                        "OTP: %s%n%n" +
                        "This OTP is valid for a limited time and must not be shared with anyone.%n" +
                        "If you did not initiate this login request, please ignore this email or contact our support team immediately.%n%n" +
                        "Kind regards,%n" +
                        "GatePay Support Team",
                firstName,
                otp
        );

        NotificationMessage message = new NotificationMessage(to, "EMAIL", subject, body, Collections.emptyMap());

        rabbitTemplate.convertAndSend("notification-exchange", "notification-routingKey", message);
    }

    /**
     * Sends login notification email
     */
    public void sendLoginEmail(String to, String firstName) {
        String subject = "Login Successful";

        String body = String.format(
                "Dear %s,%n%n" +
                        "You have successfully logged in to your account.%n%n" +
                        "If this activity was initiated by you, no further action is required.%n" +
                        "If you do not recognize this login, please contact our support team immediately.%n%n" +
                        "Best regards,%n" +
                        "GatePay Support Team",
                firstName
        );

        NotificationMessage message = new NotificationMessage(to, "EMAIL", subject, body, Collections.emptyMap());

        rabbitTemplate.convertAndSend("notification-exchange", "notification-routingKey", message);
    }



//     Sends generic reset password success email

    public void sendResetPasswordSuccessEmail(String to, String newPassword, String firstName) {
        String subject = "Password Reset Successful";

        String body = String.format(
                "Dear %s,%n%n" +
                        "Your password has been successfully reset.%n%n" +
                        "Your temporary password is:%n" +
                        "%s%n%n" +
                        "For security reasons, please log in and change this password immediately.%n%n" +
                        "If you did not initiate this request, kindly contact our support team without delay.%n%n" +
                        "Best regards,%n" +
                        "GatePay Support Team",
                firstName,
                newPassword
        );

        NotificationMessage message = new NotificationMessage(to, "EMAIL", subject, body, Collections.emptyMap());

        rabbitTemplate.convertAndSend("notification-exchange", "notification-routingKey", message);
    }

}