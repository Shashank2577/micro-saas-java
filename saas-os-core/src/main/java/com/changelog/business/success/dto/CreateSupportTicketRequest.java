package com.changelog.business.success.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateSupportTicketRequest {

    private UUID customerId;
    private String subject;
    private String description;
    private String priority; // low | normal | high | urgent
    private String channel;  // email | chat | widget | api
}
