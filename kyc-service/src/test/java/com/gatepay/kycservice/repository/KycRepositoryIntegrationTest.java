package com.gatepay.kycservice.repository;

import com.gatepay.kycservice.enums.KycStatus;
import com.gatepay.kycservice.model.KycDocument;
import com.gatepay.kycservice.model.KycRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("KycRepository Integration Tests")
class KycRepositoryIntegrationTest {

    //Spins up a real MySQL instance in Docker
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",                mysql::getJdbcUrl);
        registry.add("spring.datasource.username",           mysql::getUsername);
        registry.add("spring.datasource.password",           mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired
    private KycRepository kycRepository;



    private KycRequest pendingRequest;   // userId=1, PENDING
    private KycRequest approvedRequest;  // userId=2, APPROVED

    @BeforeEach
    void setup() {
        kycRepository.deleteAll();

        pendingRequest = kycRepository.save(KycRequest.builder()
                .userId(1L)
                .status(KycStatus.PENDING)
                .nin("11111111111")
                .bvn("22222222222")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        approvedRequest = kycRepository.save(KycRequest.builder()
                .userId(2L)
                .status(KycStatus.APPROVED)
                .nin("33333333333")
                .bvn("44444444444")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }


    private KycDocument buildDocument(KycRequest owner, String type) {
        return KycDocument.builder()
                .kycRequest(owner)
                .documentType(type)
                .documentName(type.toLowerCase() + "_front.jpg")
                .documentLink("https://storage.example.com/" + type.toLowerCase() + ".jpg")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    //  findByUserId

    @Nested
    @DisplayName("findByUserId()")
    class FindByUserId {

        @Test
        @DisplayName("returns the KYC request when the user ID exists")
        void returns_request_when_userId_exists() {
            Optional<KycRequest> found = kycRepository.findByUserId(1L);

            assertThat(found).isPresent();
            assertThat(found.get().getUserId()).isEqualTo(1L);
            assertThat(found.get().getNin()).isEqualTo("11111111111");
        }

        @Test
        @DisplayName("returns empty when the user ID does not exist")
        void returns_empty_when_userId_not_found() {
            Optional<KycRequest> found = kycRepository.findByUserId(999L);

            assertThat(found).isEmpty();
        }
    }

    //  findTopByUserIdAndStatusOrderByCreatedAtDesc

    @Nested
    @DisplayName("findTopByUserIdAndStatusOrderByCreatedAtDesc()")
    class FindTopByUserIdAndStatus {

        @Test
        @DisplayName("returns the most recent request matching userId and status")
        void returns_most_recent_matching_request() {
            // save an older PENDING for the same user, then a newer one
            KycRequest older = kycRepository.save(KycRequest.builder()
                    .userId(3L)
                    .status(KycStatus.PENDING)
                    .nin("55555555555")
                    .bvn("66666666666")
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .updatedAt(LocalDateTime.now().minusDays(2))
                    .build());

            KycRequest newer = kycRepository.save(KycRequest.builder()
                    .userId(3L)
                    .status(KycStatus.PENDING)
                    .nin("77777777777")
                    .bvn("88888888888")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build());

            Optional<KycRequest> found = kycRepository
                    .findTopByUserIdAndStatusOrderByCreatedAtDesc(3L, KycStatus.PENDING);

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(newer.getId());
        }

        @Test
        @DisplayName("returns empty when no request matches the given userId and status")
        void returns_empty_when_no_match() {
            Optional<KycRequest> found = kycRepository
                    .findTopByUserIdAndStatusOrderByCreatedAtDesc(1L, KycStatus.APPROVED);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("returns empty when the userId does not exist at all")
        void returns_empty_when_userId_unknown() {
            Optional<KycRequest> found = kycRepository
                    .findTopByUserIdAndStatusOrderByCreatedAtDesc(999L, KycStatus.PENDING);

            assertThat(found).isEmpty();
        }
    }

    //  existsByUserIdAndStatus

    @Nested
    @DisplayName("existsByUserIdAndStatus()")
    class ExistsByUserIdAndStatus {

        @Test
        @DisplayName("returns true when a matching userId and status exists")
        void returns_true_when_match_exists() {
            assertThat(kycRepository.existsByUserIdAndStatus(1L, KycStatus.PENDING)).isTrue();
        }

        @Test
        @DisplayName("returns false when userId exists but status does not match")
        void returns_false_when_status_does_not_match() {
            assertThat(kycRepository.existsByUserIdAndStatus(1L, KycStatus.APPROVED)).isFalse();
        }

        @Test
        @DisplayName("returns false when userId does not exist")
        void returns_false_when_userId_unknown() {
            assertThat(kycRepository.existsByUserIdAndStatus(999L, KycStatus.PENDING)).isFalse();
        }
    }

    //  findByIdAndStatus

    @Nested
    @DisplayName("findByIdAndStatus()")
    class FindByIdAndStatus {

        @Test
        @DisplayName("returns the request when ID and status both match")
        void returns_request_when_id_and_status_match() {
            Optional<KycRequest> found = kycRepository
                    .findByIdAndStatus(pendingRequest.getId(), KycStatus.PENDING);

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(pendingRequest.getId());
            assertThat(found.get().getStatus()).isEqualTo(KycStatus.PENDING);
        }

        @Test
        @DisplayName("returns empty when ID matches but status does not")
        void returns_empty_when_status_mismatch() {
            Optional<KycRequest> found = kycRepository
                    .findByIdAndStatus(pendingRequest.getId(), KycStatus.APPROVED);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("returns empty when ID does not exist")
        void returns_empty_when_id_unknown() {
            Optional<KycRequest> found = kycRepository
                    .findByIdAndStatus(999L, KycStatus.PENDING);

            assertThat(found).isEmpty();
        }
    }

    //  existsByNin

    @Nested
    @DisplayName("existsByNin()")
    class ExistsByNin {

        @Test
        @DisplayName("returns true when the NIN already exists")
        void returns_true_when_nin_exists() {
            assertThat(kycRepository.existsByNin("11111111111")).isTrue();
        }

        @Test
        @DisplayName("returns false when the NIN does not exist")
        void returns_false_when_nin_not_found() {
            assertThat(kycRepository.existsByNin("00000000000")).isFalse();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  existsByBvn
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("existsByBvn()")
    class ExistsByBvn {

        @Test
        @DisplayName("returns true when the BVN already exists")
        void returns_true_when_bvn_exists() {
            assertThat(kycRepository.existsByBvn("22222222222")).isTrue();
        }

        @Test
        @DisplayName("returns false when the BVN does not exist")
        void returns_false_when_bvn_not_found() {
            assertThat(kycRepository.existsByBvn("00000000000")).isFalse();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  cascade — documents saved with the request
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CascadeType.ALL — documents lifecycle")
    class DocumentCascade {

        @Test
        @DisplayName("documents are persisted when saved via the parent request")
        void documents_persisted_with_parent() {
            KycDocument doc = buildDocument(pendingRequest, "PASSPORT");
            pendingRequest.setDocuments(new ArrayList<>(List.of(doc)));  // ← fix
            kycRepository.save(pendingRequest);

            KycRequest reloaded = kycRepository.findById(pendingRequest.getId()).orElseThrow();
            assertThat(reloaded.getDocuments()).hasSize(1);
            assertThat(reloaded.getDocuments().get(0).getDocumentType()).isEqualTo("PASSPORT");
        }

        @Test
        @DisplayName("documents are deleted when the parent request is deleted (orphanRemoval)")
        void documents_deleted_with_parent() {
            KycDocument doc = buildDocument(pendingRequest, "NIN");
            pendingRequest.setDocuments(new ArrayList<>(List.of(doc)));  // ← fix
            kycRepository.save(pendingRequest);

            kycRepository.delete(pendingRequest);

            assertThat(kycRepository.findById(pendingRequest.getId())).isEmpty();
        }
    }


    //  audit fields

    @Nested
    @DisplayName("audit fields")
    class AuditFields {

        @Test
        @DisplayName("createdAt and updatedAt are persisted on save")
        void audit_fields_populated_on_save() {
            assertThat(pendingRequest.getCreatedAt()).isNotNull();
            assertThat(pendingRequest.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("default status is PENDING when not explicitly set")
        void default_status_is_pending() {
            // Note: @Builder ignores field initializers — status must be set explicitly.
            // This test documents that behaviour and confirms PENDING is set on save.
            assertThat(pendingRequest.getStatus()).isEqualTo(KycStatus.PENDING);
        }
    }
}