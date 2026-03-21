package com.gatepay.gatewayservice.config;

import com.gatepay.gatewayservice.security.JwtAuthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewaySecurityConfig.class);

    private final JwtAuthFilter jwtAuthFilter;
    private final CustomGatewayProperties gatewayProperties;

    public GatewaySecurityConfig(JwtAuthFilter jwtAuthFilter, CustomGatewayProperties gatewayProperties) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.gatewayProperties = gatewayProperties;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        log.info("Initializing Gateway Security Configuration");

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

                .authorizeExchange(exchanges -> exchanges
                        // Permit all public paths
                        .pathMatchers(gatewayProperties.getPublicPaths().toArray(new String[0])).permitAll()
                        // Admin endpoints
                        .pathMatchers("/api/v1/users/admin/**").hasAuthority("ROLE_ADMIN")
                        .pathMatchers("/api/v1/kyc/admin/**").hasAuthority("ROLE_ADMIN")
                        .pathMatchers("/api/v1/payments/admin/**").hasAuthority("ROLE_ADMIN")
                        .pathMatchers("/api/v1/wallets/admin/**").hasAuthority("ROLE_ADMIN")

                        // User endpoints
                        .pathMatchers("/api/v1/users/**").hasAuthority("ROLE_USER")
                        .pathMatchers("/api/v1/kyc/**").hasAuthority("ROLE_USER")
                        .pathMatchers("/api/v1/payments/**").hasAuthority("ROLE_USER")
                        .pathMatchers("/api/v1/wallets/**").hasAuthority("ROLE_USER")
                        // All other endpoints require authentication
                        .anyExchange().authenticated()
                )

                // Add JWT filter
                .addFilterAt(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((exchange, ex) -> {
                            log.warn("Unauthorized access attempt: {}", ex.getMessage());
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            String body = "{\"statusCode\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required. Please provide a valid JWT token.\"}";
                            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
                        })
                        .accessDeniedHandler((exchange, ex) -> {
                            log.warn("Access denied: {}", ex.getMessage());
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            String body = "{\"statusCode\":403,\"error\":\"Forbidden\",\"message\":\"Access denied. You don't have permission to access this resource.\"}";
                            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
                        })
                )
                .build();
    }
}
