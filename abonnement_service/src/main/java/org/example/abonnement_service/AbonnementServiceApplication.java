package org.example.abonnement_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication (scanBasePackages = {"org.example.abonnement_service", "org.example.common"})
@EnableFeignClients
@EnableScheduling
public class AbonnementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AbonnementServiceApplication.class, args);
    }

}
