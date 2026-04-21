package com.changelog.apikey.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class RegisterConsumerRequest {
    private String externalId;
    private String name;
    private String email;
    private String planTier;
}
