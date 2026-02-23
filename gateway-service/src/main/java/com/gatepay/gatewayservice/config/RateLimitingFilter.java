package com.gatepay.gatewayservice.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String key = resolveKey(exchange);
        Bucket bucket = buckets.computeIfAbsent(key, this::newBucket);

        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        }

        return writeErrorResponse(exchange);
    }

    private Mono<Void> writeErrorResponse(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse error = new ErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too many requests",
                "Rate limit exceeded. Please try again later."
        );

        try {
            byte[] bytes = objectMapper
                    .writeValueAsString(error)
                    .getBytes(StandardCharsets.UTF_8);

            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse()
                            .bufferFactory()
                            .wrap(bytes)));
        } catch (Exception e) {
            return exchange.getResponse().setComplete();
        }
    }

    private Bucket newBucket(String key) {
        Bandwidth limit = Bandwidth.classic(
                20, //  small for testing
                Refill.greedy(5, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveKey(ServerWebExchange exchange) {
        return exchange.getRequest()
                .getRemoteAddress()
                .getAddress()
                .getHostAddress();
    }

    @Override
    public int getOrder() {
        return -1;
    }

    record ErrorResponse(int status, String error, String message) {}
}
