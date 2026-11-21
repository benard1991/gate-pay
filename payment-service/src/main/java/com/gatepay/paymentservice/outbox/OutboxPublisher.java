package com.gatepay.paymentservice.outbox;

import com.gatepay.paymentservice.model.OutboxEvent;
import com.gatepay.paymentservice.model.enums.OutboxStatus;
import com.gatepay.paymentservice.producer.PaymentEventProducer;
import com.gatepay.paymentservice.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// Polls outbox_events and sends pending events to RabbitMQ.
// Delivery is at-least-once: a crash between the broker ack and saving SENT means
// the event goes out again, so consumers need to handle duplicates.
@Slf4j
@Component
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final TransactionTemplate transactionTemplate;

    private final int batchSize;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           PaymentEventProducer paymentEventProducer,
                           TransactionTemplate transactionTemplate,
                           @Value("${outbox.publisher.batch-size:50}") int batchSize,
                           @Value("${outbox.publisher.max-attempts:10}") int maxAttempts,
                           @Value("${outbox.publisher.base-backoff-ms:2000}") long baseBackoffMs,
                           @Value("${outbox.publisher.max-backoff-ms:300000}") long maxBackoffMs) {
        this.outboxEventRepository = outboxEventRepository;
        this.paymentEventProducer = paymentEventProducer;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.baseBackoff = Duration.ofMillis(baseBackoffMs);
        this.maxBackoff = Duration.ofMillis(maxBackoffMs);
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:1000}")
    public void publishPendingEvents() {
        try {
            Integer published = transactionTemplate.execute(status -> publishBatch());
            if (published != null && published > 0) {
                log.info("Outbox relay published {} event(s)", published);
            }
        } catch (Exception e) {
            // DB down or similar, we'll try again on the next run
            log.error("Outbox relay run failed: {}", e.getMessage(), e);
        }
    }

    // called inside a transaction so the row locks hold until we save the statuses
    int publishBatch() {
        List<OutboxEvent> events = outboxEventRepository.lockDueEvents(LocalDateTime.now(), batchSize);
        int published = 0;

        for (OutboxEvent event : events) {
            try {
                paymentEventProducer.publishConfirmed(
                        event.getExchangeName(), event.getRoutingKey(), event.getPayload());
                event.setStatus(OutboxStatus.SENT);
                event.setSentAt(LocalDateTime.now());
                event.setLastError(null);
                published++;
            } catch (Exception e) {
                recordFailure(event, e);
            }
            event.setAttempts(event.getAttempts() + 1);
        }

        outboxEventRepository.saveAll(events);
        return published;
    }

    private void recordFailure(OutboxEvent event, Exception e) {
        int attempt = event.getAttempts() + 1;
        event.setLastError(truncate(e.getMessage()));

        if (attempt >= maxAttempts) {
            event.setStatus(OutboxStatus.FAILED);
            log.error("Outbox event FAILED permanently after {} attempts | id={} | type={} | aggregateId={} | error={}",
                    attempt, event.getId(), event.getEventType(), event.getAggregateId(), e.getMessage());
            return;
        }

        Duration delay = backoffFor(attempt);
        event.setNextAttemptAt(LocalDateTime.now().plus(delay));
        log.warn("Outbox publish failed, retrying in {}s | id={} | attempt={} | error={}",
                delay.toSeconds(), event.getId(), attempt, e.getMessage());
    }

    // 2s, 4s, 8s ... up to maxBackoff
    Duration backoffFor(int attempt) {
        int shift = Math.min(attempt - 1, 20);
        Duration delay = baseBackoff.multipliedBy(1L << shift);
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }

    private static String truncate(String msg) {
        if (msg == null) return null;
        return msg.length() <= 1000 ? msg : msg.substring(0, 1000);
    }
}
