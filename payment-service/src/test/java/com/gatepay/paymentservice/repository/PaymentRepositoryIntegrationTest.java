package com.gatepay.paymentservice.repository;

import com.gatepay.paymentservice.model.Payment;
import com.gatepay.paymentservice.model.enums.PaymentProvider;
import com.gatepay.paymentservice.model.enums.TransactionStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PaymentRepository Integration Tests")
class PaymentRepositoryIntegrationTest {

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
    private PaymentRepository repository;

    private Payment payment1;
    private Payment payment2;

    @BeforeEach
    void setup() {
        repository.deleteAll();

        payment1 = repository.save(Payment.builder()
                .userId(1L)
                .amount(new BigDecimal("5000.00"))
                .paymentProvider(PaymentProvider.PAYSTACK)
                .reference("PAY-REF-001")
                .status(TransactionStatus.SUCCESS)
                .narration("Test payment one")
                .wallet(null)
                .build());

        payment2 = repository.save(Payment.builder()
                .userId(2L)
                .amount(new BigDecimal("10000.00"))
                .paymentProvider(PaymentProvider.FLUTTERWAVE)
                .reference("PAY-REF-002")
                .status(TransactionStatus.PENDING)
                .narration("Test payment two")
                .wallet(null)
                .build());
    }

    // findByReference

    @Nested
    @DisplayName("findByReference()")
    class FindByReference {

        @Test
        void returns_payment_when_reference_exists() {
            Optional<Payment> found = repository.findByReference("PAY-REF-001");

            assertThat(found).isPresent();
            assertThat(found.get().getReference()).isEqualTo("PAY-REF-001");
            assertThat(found.get().getUserId()).isEqualTo(1L);
            assertThat(found.get().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
            assertThat(found.get().getPaymentProvider()).isEqualTo(PaymentProvider.PAYSTACK);
        }

        @Test
        void returns_empty_when_reference_does_not_exist() {
            Optional<Payment> found = repository.findByReference("NON-EXISTENT-REF");

            assertThat(found).isEmpty();
        }
    }


    // existsByReference

    @Nested
    @DisplayName("existsByReference()")
    class ExistsByReference {

        @Test
        void returns_true_when_reference_exists() {
            boolean exists = repository.existsByReference("PAY-REF-001");

            assertThat(exists).isTrue();
        }

        @Test
        void returns_false_when_reference_does_not_exist() {
            boolean exists = repository.existsByReference("NON-EXISTENT-REF");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        void persists_payment_with_generated_id() {
            assertThat(payment1.getId()).isNotNull();
            assertThat(payment2.getId()).isNotNull();
        }

        @Test
        void enforces_unique_reference_constraint() {
            Payment duplicate = Payment.builder()
                    .userId(3L)
                    .amount(new BigDecimal("1000.00"))
                    .paymentProvider(PaymentProvider.PAYSTACK)
                    .reference("PAY-REF-001") // duplicate reference
                    .status(TransactionStatus.PENDING)
                    .wallet(null)
                    .build();

            org.junit.jupiter.api.Assertions.assertThrows(
                    Exception.class,
                    () -> repository.saveAndFlush(duplicate)
            );
        }
    }

    // findAll / count

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        void returns_all_saved_payments() {
            assertThat(repository.findAll()).hasSize(2);
        }

        @Test
        void count_matches_saved_payments() {
            assertThat(repository.count()).isEqualTo(2);
        }
    }

    // deleteAll
    @Nested
    @DisplayName("deleteAll()")
    class DeleteAll {

        @Test
        void removes_all_payments() {
            repository.deleteAll();

            assertThat(repository.findAll()).isEmpty();
        }
    }
}