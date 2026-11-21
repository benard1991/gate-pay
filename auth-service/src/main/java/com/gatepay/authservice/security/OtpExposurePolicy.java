package com.gatepay.authservice.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Returning the OTP in the API response is a local-testing shortcut only.
// Off by default: when off, the OTP is delivered by email and nowhere else.
@Slf4j
@Component
public class OtpExposurePolicy {

    private final boolean exposeInResponse;

    public OtpExposurePolicy(@Value("${gatepay.security.expose-otp-in-response:false}") boolean exposeInResponse) {
        this.exposeInResponse = exposeInResponse;
        if (exposeInResponse) {
            log.warn("OTPs are being returned in API responses. Never enable this outside local development.");
        }
    }

    // Returns the OTP when exposure is enabled, otherwise null (omitted from JSON)
    public String forResponse(String otp) {
        return exposeInResponse ? otp : null;
    }
}
