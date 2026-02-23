package com.gatepay.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class JwtTokens {
    private String accessToken;
    private Instant accessTokenExpiresAt;
    private String refreshToken;
    private Instant refreshTokenExpiresAt;
}