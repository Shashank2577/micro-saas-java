package com.changelog.issuetracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
@ComponentScan(basePackages = {"com.changelog.issuetracker", "com.changelog.ai", "com.changelog.config"})
public class IssueTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(IssueTrackerApplication.class, args);
    }
}
