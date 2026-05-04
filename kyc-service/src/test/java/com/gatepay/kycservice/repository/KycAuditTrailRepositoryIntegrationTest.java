package com.gatepay.kycservice.repository;

import com.gatepay.kycservice.enums.KycStatus;
import com.gatepay.kycservice.model.KycAuditTrail;
import com.gatepay.kycservice.model.KycRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("KycAuditTrailRepository Integration Tests")
class KycAuditTrailRepositoryIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private KycAuditTrailRepository auditRepository;

    @Autowired
    private KycRepository kycRepository;

    private KycRequest kycRequest;

    @BeforeEach
    void setup() {
        auditRepository.deleteAll();
        kycRepository.deleteAll();

        //  Create parent KYC request FIRST (fixes FK issue)
        kycRequest = kycRepository.save(KycRequest.builder()
                .userId(1L)
                .status(KycStatus.PENDING)
                .nin("11111111111")
                .bvn("22222222222")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        //  Now create audit logs using REAL kyc_id
        auditRepository.save(KycAuditTrail.builder()
                .kycId(kycRequest.getId())
                .action("CREATED")
                .performedBy(1L)
                .performedAt(LocalDateTime.now().minusMinutes(10))
                .build());

        auditRepository.save(KycAuditTrail.builder()
                .kycId(kycRequest.getId())
                .action("UPDATED")
                .performedBy(2L)
                .performedAt(LocalDateTime.now().minusMinutes(5))
                .build());

        auditRepository.save(KycAuditTrail.builder()
                .kycId(kycRequest.getId())
                .action("APPROVED")
                .performedBy(3L)
                .performedAt(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("returns audit trails ordered by performedAt descending")
    void returns_audit_trails_ordered_desc() {

        Page<KycAuditTrail> result = auditRepository
                .findByKycIdOrderByPerformedAtDesc(
                        kycRequest.getId(),
                        PageRequest.of(0, 10)
                );

        assertThat(result.getContent()).hasSize(3);

        assertThat(result.getContent().get(0).getAction()).isEqualTo("APPROVED");
        assertThat(result.getContent().get(1).getAction()).isEqualTo("UPDATED");
        assertThat(result.getContent().get(2).getAction()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("returns empty page when kycId does not exist")
    void returns_empty_when_kycId_not_found() {

        Page<KycAuditTrail> result = auditRepository
                .findByKycIdOrderByPerformedAtDesc(999L, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("respects pagination (limit results)")
    void respects_pagination() {

        Page<KycAuditTrail> result = auditRepository
                .findByKycIdOrderByPerformedAtDesc(
                        kycRequest.getId(),
                        PageRequest.of(0, 2)
                );

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
    }
}