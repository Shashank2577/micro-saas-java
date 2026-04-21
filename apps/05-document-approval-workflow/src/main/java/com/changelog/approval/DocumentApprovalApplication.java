package com.changelog.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaAuditing
@ComponentScan(basePackages = {"com.changelog.approval", "com.changelog.config"})
@EntityScan(basePackages = {"com.changelog.approval.model", "com.changelog.model"})
@EnableJpaRepositories(basePackages = {"com.changelog.approval.repository", "com.changelog.repository"})
public class DocumentApprovalApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentApprovalApplication.class, args);
    }
}
