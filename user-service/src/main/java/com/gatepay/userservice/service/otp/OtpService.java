package com.gatepay.userservice.service.otp;

public interface OtpService {
    String generateOtp();
    void storeOtp(String key, String otp, long expiryMinutes);
    boolean validateOtp(String key, String submittedOtp);
    public boolean verifyOtp(String userIdentifier, String submittedOtp);
    void deleteOtp(String key);
}
