package com.gatepay.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(
        exclude = {
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
                org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class
        }
)
@EnableFeignClients(basePackages = "com.gatepay.authservice.client")
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}

