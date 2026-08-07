package org.example.storeintegration;

import org.example.storeintegration.config.IntegrationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"org.example.storeintegration", "org.example.common"})
@EnableConfigurationProperties(IntegrationProperties.class)
public class StoreIntegrationApplication {
    public static void main(String[] args) {
        SpringApplication.run(StoreIntegrationApplication.class, args);
    }
}
