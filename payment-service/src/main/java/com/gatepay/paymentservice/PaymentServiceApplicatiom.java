package com.gatepay.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;



@SpringBootApplication()
@EnableFeignClients(basePackages = "com.gatepay.paymentservice.client")
public class PaymentServiceApplicatiom {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplicatiom.class, args);
    }
}