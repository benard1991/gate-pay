package com.gatepay.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;



@SpringBootApplication()
@EnableFeignClients(basePackages = "com.gatepay.paymentservice.client")
@EnableScheduling
public class PaymentServiceApplicatiom {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplicatiom.class, args);
    }
}