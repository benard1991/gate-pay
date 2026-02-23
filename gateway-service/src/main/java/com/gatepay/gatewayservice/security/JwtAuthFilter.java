package com.gatepay.gatewayservice.security;

import com.gatepay.gatewayservice.config.CustomGatewayProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthFilter implements WebFilter {

    private final JwtUtil jwtUtil;
    private final CustomGatewayProperties gatewayProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthFilter(JwtUtil jwtUtil, CustomGatewayProperties gatewayProperties) {
        this.jwtUtil = jwtUtil;
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = normalizePath(exchange.getRequest().getPath().value());

        // Skip public paths
        for (String publicPath : gatewayProperties.getPublicPaths()) {
            if (pathMatcher.match(normalizePath(publicPath), path)) {
                return chain.filter(exchange);
            }
        }

        // Extract JWT token
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            return chain.filter(exchange);
        }

        // Extract username and roles
        String username = jwtUtil.extractUsername(token);
        List<String> roles = jwtUtil.extractRoles(token);

        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());

        // Create authentication token
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, authorities);

        // Inject X-Username header for downstream services
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate()
                        .header("X-Username", username)
                        .build())
                .build();

        return chain.filter(mutatedExchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private String normalizePath(String path) {
        if (path.endsWith("/") && path.length() > 1) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
