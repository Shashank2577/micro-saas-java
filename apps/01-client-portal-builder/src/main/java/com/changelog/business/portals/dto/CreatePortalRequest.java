package com.changelog.business.portals.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreatePortalRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String slug;

    private String clientName;

    private String branding;
}
