package com.gatepay.walletservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.walletservice.client.UserServiceClient;
import com.gatepay.walletservice.dto.ApiResponse;
import com.gatepay.walletservice.dto.UserDto;
import com.gatepay.walletservice.dto.external.NotificationMessage;
import com.gatepay.walletservice.model.WalletTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletEventServiceImpl implements WalletEventService {

    private final RabbitTemplate    rabbitTemplate;
    private final ObjectMapper      objectMapper;
    private final UserServiceClient userServiceClient;

    @Value("${wallet.rabbitmq.exchange:wallet.exchange}")
    private String exchange;

    @Value("${wallet.rabbitmq.routing-key.credit:wallet.credit}")
    private String creditRoutingKey;

    @Value("${wallet.rabbitmq.routing-key.debit:wallet.debit}")
    private String debitRoutingKey;

    @Value("${wallet.rabbitmq.routing-key.reversal:wallet.reversal}")
    private String reversalRoutingKey;

    @Value("${wallet.rabbitmq.routing-key.created:wallet.created}")
    private String createdRoutingKey;

    @Value("${wallet.rabbitmq.routing-key.suspended:wallet.suspended}")
    private String suspendedRoutingKey;

    @Value("${notification.rabbitmq.exchange:notification.exchange}")
    private String notificationExchange;

    @Value("${notification.rabbitmq.routing-key:notification.routing.key}")
    private String notificationRoutingKey;

    // ─────────────────────────────────────────
    // CREDIT
    // ─────────────────────────────────────────
    @Override
    public void publishCreditEvent(WalletTransaction tx) {
        publish(creditRoutingKey, buildTransactionPayload(tx));
        publishNotification(tx,
                "Wallet Credited",
                "Your wallet has been credited with ₦" + tx.getAmount()
                        + ". Reference: " + tx.getReference()
                        + ". New balance: ₦" + tx.getBalanceAfter()
        );
    }

    // ─────────────────────────────────────────
    // DEBIT
    // ─────────────────────────────────────────
    @Override
    public void publishDebitEvent(WalletTransaction tx) {
        publish(debitRoutingKey, buildTransactionPayload(tx));
        publishNotification(tx,
                "Wallet Debited",
                "Your wallet has been debited with ₦" + tx.getAmount()
                        + ". Reference: " + tx.getReference()
                        + ". New balance: ₦" + tx.getBalanceAfter()
        );
    }

    // ─────────────────────────────────────────
    // REVERSAL
    // ─────────────────────────────────────────
    @Override
    public void publishReversalEvent(WalletTransaction tx) {
        publish(reversalRoutingKey, buildTransactionPayload(tx));
        publishNotification(tx,
                "Transaction Reversed",
                "A transaction of ₦" + tx.getAmount()
                        + " has been reversed. Reference: " + tx.getReference()
        );
    }

    // ─────────────────────────────────────────
    // WALLET CREATED
    // ─────────────────────────────────────────
    @Override
    public void publishWalletCreatedEvent(Long userId, String currency) {
        publish(createdRoutingKey, Map.of(
                "userId", userId,
                "currency", currency,
                "event", "WALLET_CREATED"
        ));
        try {
            NotificationMessage message = new NotificationMessage(
                    getUserEmail(userId),
                    "EMAIL",
                    "Wallet Created Successfully",
                    "Your " + currency + " wallet has been created successfully.",
                    Map.of("userId", userId, "currency", currency)
            );
            rabbitTemplate.convertAndSend(notificationExchange, notificationRoutingKey, message);
        } catch (Exception e) {
            log.error("Failed to publish wallet created notification: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // WALLET SUSPENDED
    // ─────────────────────────────────────────
    @Override
    public void publishWalletSuspendedEvent(Long userId) {
        publish(suspendedRoutingKey, Map.of(
                "userId", userId,
                "event", "WALLET_SUSPENDED"
        ));
        try {
            NotificationMessage message = new NotificationMessage(
                    getUserEmail(userId),
                    "EMAIL",
                    "Wallet Suspended",
                    "Your wallet has been suspended. Please contact support for assistance.",
                    Map.of("userId", userId)
            );
            rabbitTemplate.convertAndSend(notificationExchange, notificationRoutingKey, message);
        } catch (Exception e) {
            log.error("Failed to publish wallet suspended notification: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────
    private void publishNotification(WalletTransaction tx, String subject, String body) {
        try {
            NotificationMessage message = new NotificationMessage(
                    getUserEmail(tx.getWallet().getUserId()),
                    "EMAIL",
                    subject,
                    body,
                    Map.of(
                            "amount",       tx.getAmount(),
                            "reference",    tx.getReference(),
                            "balanceAfter", tx.getBalanceAfter(),
                            "userId",       tx.getWallet().getUserId()
                    )
            );
            rabbitTemplate.convertAndSend(notificationExchange, notificationRoutingKey, message);
        } catch (Exception e) {
            log.error("Failed to publish notification: {}", e.getMessage());
        }
    }

    private void publish(String routingKey, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            rabbitTemplate.convertAndSend(exchange, routingKey, json);
            log.info("Published event to exchange: {} routingKey: {}", exchange, routingKey);
        } catch (Exception e) {
            log.error("Failed to publish event routingKey: {} error: {}", routingKey, e.getMessage());
        }
    }

    private Map<String, Object> buildTransactionPayload(WalletTransaction tx) {
        return Map.of(
                "transactionId", tx.getId(),
                "walletId",      tx.getWallet().getId(),
                "userId",        tx.getWallet().getUserId(),
                "reference",     tx.getReference(),
                "type",          tx.getType().name(),
                "source",        tx.getSource().name(),
                "amount",        tx.getAmount(),
                "balanceAfter",  tx.getBalanceAfter(),
                "status",        tx.getStatus().name()
        );
    }

    private String getUserEmail(Long userId) {
        try {
            ApiResponse<UserDto> response = userServiceClient.getUserProfile(userId);
            if (response != null && response.getData() != null) {
                return response.getData().getEmail();
            }
            log.warn("Empty profile response for userId: {}", userId);
            return "";
        } catch (Exception e) {
            log.error("Failed to fetch profile for userId: {} error: {}", userId, e.getMessage());
            return "";
        }
    }
}