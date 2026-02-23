package com.gatepay.authservice.service;

import com.gatepay.authservice.dto.JwtTokens;
import com.gatepay.authservice.dto.UserDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtService(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }


    public JwtTokens generateTokens(UserDto user) {
        Instant now = Instant.now();
        Instant accessExp = now.plusMillis(accessTokenExpirationMs);
        Instant refreshExp = now.plusMillis(refreshTokenExpirationMs);

        String accessToken = Jwts.builder()
                .setSubject(user.getEmail())
                .claim("roles", user.getRoles())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(accessExp))
                .signWith(signingKey)
                .compact();

        String refreshToken = Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(refreshExp))
                .signWith(signingKey)
                .compact();

        return new JwtTokens(accessToken, accessExp, refreshToken, refreshExp);
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }


    public List<String> extractRoles(String token) {
        Claims claims = extractClaims(token);
        Object roles = claims.get("roles");
        if (roles instanceof List<?>) {
            return ((List<?>) roles).stream().map(Object::toString).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }


    public boolean isTokenValid(String token, UserDetails userDetails) {
        return extractUsername(token).equals(userDetails.getUsername()) && !isTokenExpired(token);
    }


    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(Date.from(Instant.now()));
    }

    public boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }


    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private List<String> extractRoles(UserDto user) {
        if (user.getRoles() != null) {
            return user.getRoles();
        }
        return Collections.emptyList();
    }

}
