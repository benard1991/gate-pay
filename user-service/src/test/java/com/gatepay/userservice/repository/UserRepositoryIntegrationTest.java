package com.gatepay.userservice.repository;

import com.gatepay.userservice.enums.AccountStatus;
import com.gatepay.userservice.model.User;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("integration-test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // don't swap MySQL for H2
@DisplayName("UserRepository Integration Tests")
class UserRepositoryIntegrationTest {

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
    private UserRepository userRepository;

    // a fresh user we can reuse across tests — saved in @BeforeEach
    private User savedUser;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();

        savedUser = userRepository.save(User.builder()
                .firstName("Ben")
                .lastName("Nwabueze")
                .email("ben@example.com")
                .password("hashed-password")
                .phoneNumber("08012345678")
                .build());
    }

    @Nested
    @DisplayName("findByEmail()")
    class FindByEmailTests {

        @Test
        @DisplayName("Returns the user when the email exists")
        void returns_user_when_email_exists() {
            Optional<User> found = userRepository.findByEmail("ben@example.com");

            assertThat(found).isPresent();
            assertThat(found.get().getFirstName()).isEqualTo("Ben");
            assertThat(found.get().getEmail()).isEqualTo("ben@example.com");
        }

        @Test
        @DisplayName("Returns empty when the email does not exist")
        void returns_empty_when_email_does_not_exist() {
            Optional<User> found = userRepository.findByEmail("ghost@example.com");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findWithRolesByEmail()")
    class FindWithRolesByEmailTests {

        @Test
        @DisplayName("Returns the user with roles eagerly loaded")
        void returns_user_with_roles_loaded() {
            Optional<User> found = userRepository.findWithRolesByEmail("ben@example.com");

            assertThat(found).isPresent();
            // roles should be an empty set, not a lazy-load exception
            assertThat(found.get().getRoles()).isNotNull();
        }

        @Test
        @DisplayName("Returns empty when no user matches the email")
        void returns_empty_when_email_not_found() {
            assertThat(userRepository.findWithRolesByEmail("nobody@example.com")).isEmpty();
        }
    }

    @Nested
    @DisplayName("findWithRolesByPhoneNumber()")
    class FindWithRolesByPhoneNumberTests {

        @Test
        @DisplayName("Returns the user when the phone number exists")
        void returns_user_when_phone_number_exists() {
            Optional<User> found = userRepository.findWithRolesByPhoneNumber("08012345678");

            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("ben@example.com");
        }

        @Test
        @DisplayName("Returns empty when the phone number does not exist")
        void returns_empty_when_phone_not_found() {
            assertThat(userRepository.findWithRolesByPhoneNumber("00000000000")).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByPhoneNumber()")
    class ExistsByPhoneNumberTests {

        @Test
        @DisplayName("Returns true when the phone number is already registered")
        void returns_true_when_phone_exists() {
            assertThat(userRepository.existsByPhoneNumber("08012345678")).isTrue();
        }

        @Test
        @DisplayName("Returns false when the phone number is not registered")
        void returns_false_when_phone_does_not_exist() {
            assertThat(userRepository.existsByPhoneNumber("09999999999")).isFalse();
        }
    }

    @Nested
    @DisplayName("updatePassword()")
    class UpdatePasswordTests {

        @Test
        @DisplayName("Updates the password and returns 1 to confirm one row was affected")
        void updates_password_successfully() {
            int rowsAffected = userRepository.updatePassword("ben@example.com", "new-hashed-password");

            assertThat(rowsAffected).isEqualTo(1);

            // verify the change actually landed in the database
            User updated = userRepository.findByEmail("ben@example.com").orElseThrow();
            assertThat(updated.getPassword()).isEqualTo("new-hashed-password");
        }

        @Test
        @DisplayName("Returns 0 when the email does not match any user")
        void returns_zero_when_email_not_found() {
            int rowsAffected = userRepository.updatePassword("ghost@example.com", "whatever");

            assertThat(rowsAffected).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("default status and audit fields")
    class DefaultFieldTests {

        @Test
        @DisplayName("New user gets PENDING_VERIFICATION status by default")
        void new_user_has_pending_verification_status() {
            assertThat(savedUser.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("New user has kycVerified set to false by default")
        void new_user_has_kyc_not_verified() {
            assertThat(savedUser.isKycVerified()).isFalse();
        }

        @Test
        @DisplayName("createdAt and updatedAt are populated on save")
        void audit_fields_are_set_on_save() {
            assertThat(savedUser.getCreatedAt()).isNotNull();
            assertThat(savedUser.getUpdatedAt()).isNotNull();
        }
    }
}