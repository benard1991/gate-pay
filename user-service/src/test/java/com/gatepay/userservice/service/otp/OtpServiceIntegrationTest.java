package com.gatepay.userservice.service.otp;

import com.gatepay.userservice.exception.InvalidOtpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("OtpService Integration Tests - user-service (real Redis + real MySQL)")
class OtpServiceIntegrationTest {


    // Spin up a real MySQL so Flyway can run migrations and JPA can validate the schema.
    // Without this the full Spring context won't start.
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    // This is what we're actually testing against — a real Redis instance.
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    // Tell Spring to use the containers instead of whatever is in application.yml.
    // Testcontainers assigns random ports so we have to resolve them at runtime like this.
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",                  mysql::getJdbcUrl);
        registry.add("spring.datasource.username",             mysql::getUsername);
        registry.add("spring.datasource.password",             mysql::getPassword);
        registry.add("spring.datasource.driver-class-name",   () -> "com.mysql.cj.jdbc.Driver");

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // We don't have a RabbitMQ container here and we don't need one.
    // Mocking it just stops Spring from complaining about a missing broker on startup.
    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OtpService otpService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String VALID_OTP  = "123456";
    private static final String OTP_PREFIX = "otp:";

    // Make sure each test starts with a clean slate so they don't affect each other.
    @BeforeEach
    void cleanRedis() {
        redisTemplate.delete(OTP_PREFIX + TEST_EMAIL);
    }

    @Nested
    @DisplayName("generateOtp()")
    class GenerateOtpTests {

        @Test
        @DisplayName("Generates a 6-digit numeric OTP")
        void generates_six_digit_otp() {
            String otp = otpService.generateOtp();
            assertThat(otp).hasSize(6).containsOnlyDigits();
        }

        @Test
        @DisplayName("Produces valid OTPs on consecutive calls")
        void produces_valid_otps_on_consecutive_calls() {
            assertThat(otpService.generateOtp()).hasSize(6).containsOnlyDigits();
            assertThat(otpService.generateOtp()).hasSize(6).containsOnlyDigits();
        }
    }

    @Nested
    @DisplayName("storeOtp()")
    class StoreOtpTests {

        @Test
        @DisplayName("Stores the OTP in Redis under the correct key")
        void stores_otp_in_redis_under_correct_key() {
            otpService.storeOtp(TEST_EMAIL, VALID_OTP, 5);
            assertThat(redisTemplate.opsForValue().get(OTP_PREFIX + TEST_EMAIL)).isEqualTo(VALID_OTP);
        }

        @Test
        @DisplayName("Stores the OTP with a TTL so it expires automatically")
        void stores_otp_with_ttl() {
            otpService.storeOtp(TEST_EMAIL, VALID_OTP, 5);

            Long ttl = redisTemplate.getExpire(OTP_PREFIX + TEST_EMAIL, TimeUnit.SECONDS);

            // TTL should be set and somewhere under the 5-minute window we configured
            assertThat(ttl).isNotNull().isGreaterThan(0).isLessThanOrEqualTo(300);
        }

        @Test
        @DisplayName("Overwrites the existing OTP when a new one is stored for the same email")
        void overwrites_existing_otp_when_new_one_is_stored() {
            otpService.storeOtp(TEST_EMAIL, "111111", 5);
            otpService.storeOtp(TEST_EMAIL, "999999", 5);

            assertThat(redisTemplate.opsForValue().get(OTP_PREFIX + TEST_EMAIL)).isEqualTo("999999");
        }
    }

    @Nested
    @DisplayName("verifyOtp()")
    class VerifyOtpTests {

        @Test
        @DisplayName("Returns true and deletes the OTP from Redis when the code is correct")
        void returns_true_and_deletes_otp_when_code_is_correct() {
            otpService.storeOtp(TEST_EMAIL, VALID_OTP, 5);

            assertThat(otpService.verifyOtp(TEST_EMAIL, VALID_OTP)).isTrue();

            // Once verified the OTP must be gone — we don't want it usable again
            assertThat(redisTemplate.opsForValue().get(OTP_PREFIX + TEST_EMAIL)).isNull();
        }

        @Test
        @DisplayName("Throws InvalidOtpException and keeps the OTP in Redis when the code is wrong")
        void throws_and_keeps_otp_when_code_is_wrong() {
            otpService.storeOtp(TEST_EMAIL, VALID_OTP, 5);

            assertThatThrownBy(() -> otpService.verifyOtp(TEST_EMAIL, "wrong-otp"))
                    .isInstanceOf(InvalidOtpException.class);

            // The real OTP should still be there so the user can try again
            assertThat(redisTemplate.opsForValue().get(OTP_PREFIX + TEST_EMAIL)).isEqualTo(VALID_OTP);
        }

        @Test
        @DisplayName("Throws InvalidOtpException when no OTP exists in Redis for the email")
        void throws_when_no_otp_exists_in_redis() {
            assertThatThrownBy(() -> otpService.verifyOtp(TEST_EMAIL, VALID_OTP))
                    .isInstanceOf(InvalidOtpException.class);
        }

        @Test
        @DisplayName("Rejects reuse of an OTP that was already verified successfully")
        void rejects_otp_reuse_after_successful_verification() {
            otpService.storeOtp(TEST_EMAIL, VALID_OTP, 5);
            otpService.verifyOtp(TEST_EMAIL, VALID_OTP);

            // Second attempt with the same code must fail — OTP should be single-use
            assertThatThrownBy(() -> otpService.verifyOtp(TEST_EMAIL, VALID_OTP))
                    .isInstanceOf(InvalidOtpException.class);
        }
    }

    @Nested
    @DisplayName("deleteOtp()")
    class DeleteOtpTests {

        @Test
        @DisplayName("Removes the OTP from Redis")
        void removes_otp_from_redis() {
            otpService.storeOtp(TEST_EMAIL, VALID_OTP, 5);
            otpService.deleteOtp(TEST_EMAIL);

            assertThat(redisTemplate.opsForValue().get(OTP_PREFIX + TEST_EMAIL)).isNull();
        }

        @Test
        @DisplayName("Does not throw when asked to delete an OTP that does not exist")
        void does_not_throw_when_deleting_nonexistent_otp() {
            // Should be a silent no-op, not an exception
            otpService.deleteOtp("nonexistent@example.com");
        }
    }

}