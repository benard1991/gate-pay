package com.gatepay.gatewayservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtUtil {

    private final SecretKey signingKey;

    public JwtUtil(@Value("${jwt.secret}") String jwtSecret) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
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

    public boolean validateToken(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration().after(new Date());
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
}


