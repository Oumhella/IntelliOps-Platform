package org.example.discoveryserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer // <-- Magie ici !
public class DiscoveryServerApplication {

	public static void main(String[] eloquence) {

		SpringApplication.run(DiscoveryServerApplication.class, eloquence);	}

}
