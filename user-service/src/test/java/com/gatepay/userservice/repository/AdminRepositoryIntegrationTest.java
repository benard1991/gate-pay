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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("AdminRepository Integration Tests")
class AdminRepositoryIntegrationTest {

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
    private AdminRepository adminRepository;

    @BeforeEach
    void setup() {
        adminRepository.deleteAll();

        // seed a couple of users so search and pagination tests have something to work with
        adminRepository.save(User.builder()
                .firstName("Ben")
                .lastName("Nwabueze")
                .email("ben@example.com")
                .password("hashed-password")
                .phoneNumber("08012345678")
                .build());

        adminRepository.save(User.builder()
                .firstName("Ada")
                .lastName("Okonkwo")
                .email("ada@example.com")
                .password("hashed-password")
                .phoneNumber("08087654321")
                .build());
    }

    @Nested
    @DisplayName("findByEmailIgnoreCase()")
    class FindByEmailIgnoreCaseTests {

        @Test
        @DisplayName("Finds the user regardless of email casing")
        void finds_user_with_mixed_case_email() {
            Optional<User> found = adminRepository.findByEmailIgnoreCase("BEN@EXAMPLE.COM");

            assertThat(found).isPresent();
            assertThat(found.get().getFirstName()).isEqualTo("Ben");
        }

        @Test
        @DisplayName("Returns empty when no user matches the email")
        void returns_empty_when_email_not_found() {
            assertThat(adminRepository.findByEmailIgnoreCase("ghost@example.com")).isEmpty();
        }
    }

    @Nested
    @DisplayName("findWithRolesByEmailIgnoreCase()")
    class FindWithRolesByEmailIgnoreCaseTests {

        @Test
        @DisplayName("Returns the user with roles eagerly loaded regardless of email casing")
        void returns_user_with_roles_loaded() {
            Optional<User> found = adminRepository.findWithRolesByEmailIgnoreCase("ADA@EXAMPLE.COM");

            assertThat(found).isPresent();
            // roles should be an accessible empty set, not a lazy-load exception
            assertThat(found.get().getRoles()).isNotNull();
        }

        @Test
        @DisplayName("Returns empty when email does not match any user")
        void returns_empty_when_email_not_found() {
            assertThat(adminRepository.findWithRolesByEmailIgnoreCase("nobody@example.com")).isEmpty();
        }
    }

    @Nested
    @DisplayName("findWithRolesById()")
    class FindWithRolesByIdTests {

        @Test
        @DisplayName("Returns the user with roles eagerly loaded when the id exists")
        void returns_user_with_roles_when_id_exists() {
            User saved = adminRepository.findByEmailIgnoreCase("ben@example.com").orElseThrow();

            Optional<User> found = adminRepository.findWithRolesById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("ben@example.com");
            assertThat(found.get().getRoles()).isNotNull();
        }

        @Test
        @DisplayName("Returns empty when the id does not exist")
        void returns_empty_when_id_not_found() {
            assertThat(adminRepository.findWithRolesById(999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllWithRoles()")
    class FindAllWithRolesTests {

        @Test
        @DisplayName("Returns a page of users with roles eagerly loaded")
        void returns_page_of_users_with_roles() {
            Pageable pageable = PageRequest.of(0, 10);

            Page<User> page = adminRepository.findAllWithRoles(pageable);

            assertThat(page.getTotalElements()).isEqualTo(2);
            // make sure roles are accessible on every user — no lazy-load exceptions
            page.getContent().forEach(u -> assertThat(u.getRoles()).isNotNull());
        }

        @Test
        @DisplayName("Respects page size and returns the correct number of users")
        void respects_page_size() {
            Pageable pageable = PageRequest.of(0, 1);

            Page<User> page = adminRepository.findAllWithRoles(pageable);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getTotalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("Returns an empty page when there are no users")
        void returns_empty_page_when_no_users() {
            adminRepository.deleteAll();

            Page<User> page = adminRepository.findAllWithRoles(PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchUsers()")
    class SearchUsersTests {

        @Test
        @DisplayName("Finds users by first name keyword")
        void finds_users_by_first_name() {
            Page<User> result = adminRepository.searchUsers("ben", PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getEmail()).isEqualTo("ben@example.com");
        }

        @Test
        @DisplayName("Finds users by last name keyword")
        void finds_users_by_last_name() {
            Page<User> result = adminRepository.searchUsers("okonkwo", PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getEmail()).isEqualTo("ada@example.com");
        }

        @Test
        @DisplayName("Finds users by email keyword")
        void finds_users_by_email() {
            Page<User> result = adminRepository.searchUsers("ada@example", PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getFirstName()).isEqualTo("Ada");
        }

        @Test
        @DisplayName("Finds users by phone number keyword")
        void finds_users_by_phone_number() {
            Page<User> result = adminRepository.searchUsers("08012345678", PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getFirstName()).isEqualTo("Ben");
        }

        @Test
        @DisplayName("Search is case insensitive")
        void search_is_case_insensitive() {
            Page<User> result = adminRepository.searchUsers("BEN", PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getEmail()).isEqualTo("ben@example.com");
        }

        @Test
        @DisplayName("Returns all matching users when keyword matches multiple records")
        void returns_multiple_users_when_keyword_matches_many() {
            // "example" appears in both emails so both users should come back
            Page<User> result = adminRepository.searchUsers("example", PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("Returns empty page when keyword matches nothing")
        void returns_empty_when_no_match() {
            Page<User> result = adminRepository.searchUsers("zzznomatch", PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Respects pagination on search results")
        void respects_pagination_on_search_results() {
            Page<User> result = adminRepository.searchUsers("example", PageRequest.of(0, 1));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(2);
        }
    }
}