package org.example.paiment_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires the Docker PostgreSQL, MinIO, Config Server, and service-discovery stack")
class PaimentServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
