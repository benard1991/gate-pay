package com.gatepay.gatewayservice.exception;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Order(-2) // Run before default WebFlux handlers
public class GlobalExceptionHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {

        HttpStatus status;
        String message;

        // ===================== UNAUTHORIZED =====================
        if (ex instanceof UnauthorizedException) {
            status = HttpStatus.UNAUTHORIZED;
            message = ex.getMessage();
        }

        // ===================== METHOD NOT ALLOWED =====================
        else if (ex instanceof ResponseStatusException rse
                && rse.getStatusCode() == HttpStatus.METHOD_NOT_ALLOWED) {

            status = HttpStatus.METHOD_NOT_ALLOWED;
            message = "HTTP method not allowed for this endpoint";
        }

        // ===================== NOT FOUND =====================
        else if (ex instanceof ResponseStatusException rse
                && rse.getStatusCode() == HttpStatus.NOT_FOUND) {

            status = HttpStatus.NOT_FOUND;
            message = "Endpoint not found";
        }

        // ===================== NOT HANDLED HERE =====================
        else {
            return Mono.error(ex);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"statusCode\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                status.value(), status.getReasonPhrase(), message, exchange.getRequest().getPath().value()
        );

        return exchange.getResponse().writeWith(
                Mono.just(
                        exchange.getResponse()
                                .bufferFactory()
                                .wrap(body.getBytes(StandardCharsets.UTF_8))
                )
        );
    }
}
