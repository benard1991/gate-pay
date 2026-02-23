package com.gatepay.userservice.service.otp;

import com.gatepay.userservice.exception.InvalidOtpException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {


    private final StringRedisTemplate redisTemplate;

    private static final String OTP_KEY_PREFIX = "otp:";

    @Override
    public String generateOtp() {
        // Generate a 6-digit OTP
        return String.valueOf(new Random().nextInt(900_000) + 100_000);
    }

    @Override
    public void storeOtp(String userIdentifier, String otp, long expiryMinutes) {
        String key = OTP_KEY_PREFIX + userIdentifier;
        redisTemplate.opsForValue().set(key, otp, expiryMinutes, TimeUnit.MINUTES);
    }

    @Override
    public boolean validateOtp(String userIdentifier, String submittedOtp) {
        String key = OTP_KEY_PREFIX + userIdentifier;
        String storedOtp = redisTemplate.opsForValue().get(key);

        if (storedOtp == null) {
            return false;
        }

        boolean isValid = storedOtp.equals(submittedOtp);
        if (isValid) {
            deleteOtp(userIdentifier);
        }

        return isValid;
    }


    @Override
    public boolean verifyOtp(String userIdentifier, String submittedOtp) {
        boolean isValid = validateOtp(userIdentifier, submittedOtp);

        if (!isValid) {
            throw new InvalidOtpException();
        }

        return true;
    }

    @Override
    public void deleteOtp(String userIdentifier) {
        String key = OTP_KEY_PREFIX + userIdentifier;
        redisTemplate.delete(key);
    }
}