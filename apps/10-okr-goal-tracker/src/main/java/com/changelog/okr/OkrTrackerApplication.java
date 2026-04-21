package com.changelog.okr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.changelog.okr", "com.changelog"})
@EntityScan(basePackages = "com.changelog")
@EnableJpaRepositories(basePackages = "com.changelog")
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
public class OkrTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OkrTrackerApplication.class, args);
    }
}
