package com.changelog.apikey.dto;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class CreateKeyRequest {
    private String name;
    private List<String> scopes;
    private String environment;
    private UUID consumerId;
}
