package com.gatepay.paymentservice.outbox;

import com.gatepay.paymentservice.model.OutboxEvent;
import com.gatepay.paymentservice.model.enums.OutboxStatus;
import com.gatepay.paymentservice.producer.PaymentEventProducer;
import com.gatepay.paymentservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPublisher Unit Tests")
class OutboxPublisherTest {

    @Mock private OutboxEventRepository repository;
    @Mock private PaymentEventProducer producer;
    @Mock private TransactionTemplate transactionTemplate;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        // maxAttempts=3, base backoff 2s, cap 10s
        publisher = new OutboxPublisher(repository, producer, transactionTemplate, 50, 3, 2000, 10_000);
    }

    private OutboxEvent pendingEvent(int attempts) {
        return OutboxEvent.builder()
                .id(1L)
                .aggregateType("PaymentTransaction")
                .aggregateId("REF-001")
                .eventType("PAYMENT_SUCCESS")
                .exchangeName("payment.exchange")
                .routingKey("payment.success")
                .payload("{\"reference\":\"REF-001\"}")
                .status(OutboxStatus.PENDING)
                .attempts(attempts)
                .nextAttemptAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("marks event SENT when broker confirms")
    void marksSentOnConfirm() throws Exception {
        OutboxEvent event = pendingEvent(0);
        when(repository.lockDueEvents(any(), eq(50))).thenReturn(List.of(event));

        int published = publisher.publishBatch();

        assertThat(published).isEqualTo(1);
        verify(producer).publishConfirmed("payment.exchange", "payment.success", "{\"reference\":\"REF-001\"}");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(event.getSentAt()).isNotNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        verify(repository).saveAll(List.of(event));
    }

    @Test
    @DisplayName("keeps event PENDING and schedules a retry when publish fails")
    void schedulesRetryOnFailure() throws Exception {
        OutboxEvent event = pendingEvent(0);
        when(repository.lockDueEvents(any(), anyInt())).thenReturn(List.of(event));
        doThrow(new RuntimeException("broker down")).when(producer).publishConfirmed(any(), any(), any());

        int published = publisher.publishBatch();

        assertThat(published).isZero();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("broker down");
        assertThat(event.getNextAttemptAt()).isAfter(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    @DisplayName("marks event FAILED after max attempts")
    void marksFailedAfterMaxAttempts() throws Exception {
        OutboxEvent event = pendingEvent(2); // this will be attempt #3 of 3
        when(repository.lockDueEvents(any(), anyInt())).thenReturn(List.of(event));
        doThrow(new RuntimeException("still down")).when(producer).publishConfirmed(any(), any(), any());

        publisher.publishBatch();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("one failing event does not block the rest of the batch")
    void failureDoesNotBlockBatch() throws Exception {
        OutboxEvent bad = pendingEvent(0);
        OutboxEvent good = pendingEvent(0);
        good.setId(2L);
        good.setPayload("{\"reference\":\"REF-002\"}");
        when(repository.lockDueEvents(any(), anyInt())).thenReturn(List.of(bad, good));
        doThrow(new RuntimeException("boom"))
                .when(producer).publishConfirmed(any(), any(), eq("{\"reference\":\"REF-001\"}"));

        int published = publisher.publishBatch();

        assertThat(published).isEqualTo(1);
        assertThat(bad.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(good.getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    @DisplayName("backoff doubles each attempt and is capped")
    void exponentialBackoff() {
        assertThat(publisher.backoffFor(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(publisher.backoffFor(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(publisher.backoffFor(3)).isEqualTo(Duration.ofSeconds(8));
        assertThat(publisher.backoffFor(4)).isEqualTo(Duration.ofSeconds(10)); // capped
    }
}
