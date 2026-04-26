package com.gatepay.authservice.service;

import com.gatepay.authservice.dto.JwtTokens;
import com.gatepay.authservice.dto.UserDto;
import com.gatepay.authservice.enums.AccountStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private UserDto user;

    private static final String TEST_SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci1nYXRlcGF5LWp3dC10ZXN0aW5nLW9ubHk=";

    @BeforeEach
    void setUp() {
        // 15 mins 900_000L and 2 4 hours 86_400_000L
        jwtService = new JwtService(TEST_SECRET, 900_000L, 86_400_000L);

        user = new UserDto();
        user.setId(1L);
        user.setEmail("benard@gatepay.com");
        user.setFirstName("Benard");
        user.setLastName("Nwabueze");
        user.setPassword("$2a$10$hashedPassword");
        user.setStatus(AccountStatus.ACTIVE);
        user.setRoles(List.of("ROLE_USER", "ROLE_ADMIN"));
    }

    @Nested
    @DisplayName("generateTokens")
    class GenerateTokens {

        @Test
        @DisplayName("generates non-blank access and refresh tokens")
        void generatesBothTokens_forActiveUser() {
            JwtTokens tokens = jwtService.generateTokens(user);

            assertThat(tokens.getAccessToken()).isNotBlank();
            assertThat(tokens.getRefreshToken()).isNotBlank();
            assertThat(tokens.getAccessToken()).isNotEqualTo(tokens.getRefreshToken());
        }

        @Test
        @DisplayName("sets expiry timestamps in the future")
        void setsExpiryTimestamps_inTheFuture() {
            JwtTokens tokens = jwtService.generateTokens(user);

            assertThat(tokens.getAccessTokenExpiresAt()).isAfter(java.time.Instant.now());
            assertThat(tokens.getRefreshTokenExpiresAt()).isAfter(java.time.Instant.now());
        }

        @Test
        @DisplayName("refresh token expires after access token")
        void refreshTokenExpiry_isLaterThanAccessTokenExpiry() {
            JwtTokens tokens = jwtService.generateTokens(user);

            // refresh token must outlive the access token — that's the whole point of it
            assertThat(tokens.getRefreshTokenExpiresAt())
                    .isAfter(tokens.getAccessTokenExpiresAt());
        }

        // replaced the flaky sleep-based test with something actually meaningful
        @Test
        @DisplayName("access token contains correct subject and roles")
        void accessToken_containsCorrectClaimsForUser() {
            JwtTokens tokens = jwtService.generateTokens(user);

            assertThat(jwtService.extractUsername(tokens.getAccessToken()))
                    .isEqualTo("benard@gatepay.com");
            assertThat(jwtService.extractRoles(tokens.getAccessToken()))
                    .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        }

        @Test
        @DisplayName("returns false for an expired token")
        void returnsFalse_forExpiredToken() throws InterruptedException {
            // 1ms expiry so it dies almost instantly
            JwtService shortLived = new JwtService(TEST_SECRET, 1L, 1L);
            String token = shortLived.generateTokens(user).getAccessToken();

            Thread.sleep(10);

            assertThat(jwtService.validateToken(token)).isFalse();
        }

        @Test
        @DisplayName("handles null roles on UserDto without throwing")
        void handlesNullRoles_withoutThrowing() {
            user.setRoles(null);

            // shouldn't blow up with NullPointerException
            JwtTokens tokens = jwtService.generateTokens(user);
            assertThat(tokens.getAccessToken()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("extractUsername")
    class ExtractUsername {

        @Test
        @DisplayName("extracts correct email from access token")
        void extractsEmail_fromAccessToken() {
            String accessToken = jwtService.generateTokens(user).getAccessToken();

            // email goes in as subject, should come back out the same
            assertThat(jwtService.extractUsername(accessToken))
                    .isEqualTo("benard@gatepay.com");
        }

        @Test
        @DisplayName("extracts correct email from refresh token")
        void extractsEmail_fromRefreshToken() {
            String refreshToken = jwtService.generateTokens(user).getRefreshToken();

            assertThat(jwtService.extractUsername(refreshToken))
                    .isEqualTo("benard@gatepay.com");
        }
    }

    @Nested
    @DisplayName("extractRoles")
    class ExtractRoles {

        @Test
        @DisplayName("extracts all roles from access token")
        void extractsRoles_fromAccessToken() {
            String accessToken = jwtService.generateTokens(user).getAccessToken();

            // both roles must survive the round trip
            assertThat(jwtService.extractRoles(accessToken))
                    .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        }

        @Test
        @DisplayName("returns empty list when token has no roles claim")
        void returnsEmptyList_whenTokenHasNoRolesClaim() {
            // refresh token has no roles claim — extractRoles must handle that gracefully
            String refreshToken = jwtService.generateTokens(user).getRefreshToken();

            assertThat(jwtService.extractRoles(refreshToken)).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when user has no roles")
        void returnsEmptyList_whenUserHasNoRoles() {
            user.setRoles(Collections.emptyList());
            String accessToken = jwtService.generateTokens(user).getAccessToken();

            // empty in, empty out — no crash
            assertThat(jwtService.extractRoles(accessToken)).isEmpty();
        }
    }

    @Nested
    @DisplayName("isTokenValid")
    class IsTokenValid {

        @Test
        @DisplayName("returns true when token subject matches UserDetails username")
        void returnsTrue_whenTokenMatchesUserDetails() {
            String accessToken = jwtService.generateTokens(user).getAccessToken();

            UserDetails userDetails = User.withUsername("benard@gatepay.com")
                    .password("irrelevant")
                    .roles("USER")
                    .build();

            assertThat(jwtService.isTokenValid(accessToken, userDetails)).isTrue();
        }

        @Test
        @DisplayName("returns false when token subject does not match UserDetails username")
        void returnsFalse_whenTokenSubjectDoesNotMatchUserDetails() {
            String accessToken = jwtService.generateTokens(user).getAccessToken();

            // token says benard, userDetails says someone else — must reject
            UserDetails differentUser = User.withUsername("hacker@evil.com")
                    .password("irrelevant")
                    .roles("USER")
                    .build();

            assertThat(jwtService.isTokenValid(accessToken, differentUser)).isFalse();
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("returns true for a freshly generated token")
        void returnsTrue_forFreshToken() {
            String accessToken = jwtService.generateTokens(user).getAccessToken();

            assertThat(jwtService.validateToken(accessToken)).isTrue();
        }

        @Test
        @DisplayName("returns false for a malformed token string")
        void returnsFalse_forMalformedToken() {
            // gateway passes raw header values — they won't always be valid JWTs
            assertThat(jwtService.validateToken("not.a.real.token")).isFalse();
        }

        @Test
        @DisplayName("returns false for an empty string")
        void returnsFalse_forEmptyString() {
            assertThat(jwtService.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("returns false for a null token")
        void returnsFalse_forNullToken() {
            assertThat(jwtService.validateToken(null)).isFalse();
        }

        @Test
        @DisplayName("returns false for a token signed with a different secret")
        void returnsFalse_forTokenSignedWithDifferentSecret() {
            // someone forged a token with their own secret — we must reject it
            String differentSecret = "ZGlmZmVyZW50LXNlY3JldC1rZXktZm9yLXRlc3Rpbmctb25seXh4eA==";
            JwtService foreignService = new JwtService(differentSecret, 900_000L, 86_400_000L);

            String foreignToken = foreignService.generateTokens(user).getAccessToken();

            assertThat(jwtService.validateToken(foreignToken)).isFalse();
        }
    }
}