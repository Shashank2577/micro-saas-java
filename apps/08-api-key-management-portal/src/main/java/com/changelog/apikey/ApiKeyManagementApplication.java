package com.changelog.apikey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.changelog")
@EntityScan(basePackages = "com.changelog")
@EnableJpaRepositories(basePackages = "com.changelog")
public class ApiKeyManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiKeyManagementApplication.class, args);
    }
}
