							package org.example.lead_service;

							import org.springframework.boot.SpringApplication;
							import org.springframework.boot.autoconfigure.SpringBootApplication;

							@SpringBootApplication (scanBasePackages = {"org.example.lead_service", "org.example.common"})
							public class LeadServiceApplication {

								public static void main(String[] args) {
									SpringApplication.run(LeadServiceApplication.class, args);
								}

							}
