package com.gatepay.userservice.config;

import feign.Logger;
import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserClientConfig {

    @Bean
    public Request.Options feignOptions() {
        return new Request.Options(5000, 10000); // connect timeout 5s, read timeout 10s
    }

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}