package com.gatepay.paymentservice.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.paymentservice.model.OutboxEvent;
import com.gatepay.paymentservice.model.enums.OutboxStatus;
import com.gatepay.paymentservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    // MANDATORY so the event is always saved in the caller's transaction.
    // If that transaction rolls back, the event goes with it.
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent save(String aggregateType,
                            String aggregateId,
                            String eventType,
                            String exchange,
                            String routingKey,
                            Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // better to fail the whole transaction than lose the event
            throw new IllegalStateException("Could not serialise outbox payload for " + aggregateId, e);
        }

        LocalDateTime now = LocalDateTime.now();
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .exchangeName(exchange)
                .routingKey(routingKey)
                .payload(json)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .build();

        OutboxEvent saved = outboxEventRepository.save(event);
        log.info("Outbox event recorded | type={} | aggregateId={}", eventType, aggregateId);
        return saved;
    }
}
