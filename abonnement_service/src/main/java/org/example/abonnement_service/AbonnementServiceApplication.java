package org.example.abonnement_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication (scanBasePackages = {"org.example.abonnement_service", "org.example.common"})
@EnableFeignClients
public class AbonnementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AbonnementServiceApplication.class, args);
    }

}
