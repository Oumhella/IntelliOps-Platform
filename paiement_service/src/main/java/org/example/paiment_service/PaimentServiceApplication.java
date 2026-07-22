package org.example.paiment_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication (scanBasePackages = {"org.example.paiment_service", "org.example.common"})
public class PaimentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaimentServiceApplication.class, args);
    }

}
