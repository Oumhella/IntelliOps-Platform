package org.example.delivery_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires the Docker PostgreSQL, Config Server, and service-discovery stack")
class DeliveryServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
