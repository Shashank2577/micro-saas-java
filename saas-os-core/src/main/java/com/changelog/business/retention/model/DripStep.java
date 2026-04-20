package com.changelog.business.retention.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DripStep {

    private Integer stepNumber;
    private Integer delayDays;
    private String subject;
    private String bodyTemplate;
}
