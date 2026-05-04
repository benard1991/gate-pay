package com.gatepay.paymentservice.repository;

import com.gatepay.paymentservice.model.PaymentTransaction;
import com.gatepay.paymentservice.model.enums.PaymentProvider;
import com.gatepay.paymentservice.model.enums.TransactionStatus;
import com.gatepay.paymentservice.model.enums.TransactionType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PaymentTransactionRepository Integration Tests")
class PaymentTransactionRepositoryIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0")
    )
            .withDatabaseName("payment_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private PaymentTransactionRepository repository;

    private PaymentTransaction tx1;
    private PaymentTransaction tx2;

    @BeforeEach
    void setup() {
        repository.deleteAll();

        tx1 = repository.save(PaymentTransaction.builder()
                .reference("REF-001")
                .provider(PaymentProvider.PAYSTACK)
                .status(TransactionStatus.SUCCESS)
                .amount(new BigDecimal("5000.00"))
                .currency("NGN")
                .userId(100L)
                .customerEmail("john@example.com")
                .build());

        tx2 = repository.save(PaymentTransaction.builder()
                .reference("REF-002")
                .provider(PaymentProvider.FLUTTERWAVE)
                .status(TransactionStatus.PENDING)
                .amount(new BigDecimal("10000.00"))
                .currency("NGN")
                .userId(100L)
                .customerEmail("john@example.com")
                .build());
    }

    // ─────────────────────────────────────────────
    // findByReference
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("findByReference()")
    class FindByReference {

        @Test
        void returns_transaction_when_found() {
            Optional<PaymentTransaction> found =
                    repository.findByReference("REF-001");

            assertThat(found).isPresent();
            assertThat(found.get().getReference()).isEqualTo("REF-001");
        }

        @Test
        void returns_empty_when_not_found() {
            assertThat(repository.findByReference("BAD-REF")).isEmpty();
        }
    }

    // ─────────────────────────────────────────────
    // existsByReference
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("existsByReference()")
    class ExistsByReference {

        @Test
        void returns_true_when_exists() {
            assertThat(repository.existsByReference("REF-001")).isTrue();
        }

        @Test
        void returns_false_when_not_exists() {
            assertThat(repository.existsByReference("NOPE")).isFalse();
        }
    }

    // ─────────────────────────────────────────────
    // findByStatus
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("findByStatus()")
    class FindByStatus {

        @Test
        void returns_transactions_by_status() {
            List<PaymentTransaction> result =
                    repository.findByStatus(TransactionStatus.SUCCESS);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getReference()).isEqualTo("REF-001");
        }
    }

    // ─────────────────────────────────────────────
    // findByCustomerEmail
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("findByCustomerEmail()")
    class FindByCustomerEmail {

        @Test
        void returns_paginated_results() {
            Page<PaymentTransaction> page =
                    repository.findByCustomerEmail(
                            "john@example.com",
                            PageRequest.of(0, 10)
                    );

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getTotalElements()).isEqualTo(2);
        }
    }

    // ─────────────────────────────────────────────
    // findTransactionsByUserId
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("findTransactionsByUserId()")
    class FindByUserId {

        @Test
        void returns_transactions_by_user_id() {
            Page<PaymentTransaction> page =
                    repository.findTransactionsByUserId(
                            "100",
                            PageRequest.of(0, 10)
                    );

            assertThat(page.getContent()).hasSize(2);
        }
    }

    // ─────────────────────────────────────────────
    // findByReferenceForUpdate
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("findByReferenceForUpdate()")
    class FindByReferenceForUpdate {

        @Test
        void returns_locked_transaction() {
            Optional<PaymentTransaction> tx =
                    repository.findByReferenceForUpdate("REF-001");

            assertThat(tx).isPresent();
            assertThat(tx.get().getReference()).isEqualTo("REF-001");
        }
    }

    // ─────────────────────────────────────────────
    // pagination
    // ─────────────────────────────────────────────
    @Nested
    @DisplayName("findAll(Pageable)")
    class FindAll {

        @Test
        void returns_all_transactions_paginated() {
            Page<PaymentTransaction> page =
                    repository.findAll(PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
        }
    }
}