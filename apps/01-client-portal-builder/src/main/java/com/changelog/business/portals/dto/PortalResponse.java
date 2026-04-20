package com.changelog.business.portals.dto;

import com.changelog.business.portals.model.Portal;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.UUID;

@Data
public class PortalResponse {
    private UUID id;
    private String name;
    private String slug;
    private String clientName;
    private String status;
    private String branding;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public static PortalResponse fromEntity(Portal portal) {
        PortalResponse response = new PortalResponse();
        response.setId(portal.getId());
        response.setName(portal.getName());
        response.setSlug(portal.getSlug());
        response.setClientName(portal.getClientName());
        response.setStatus(portal.getStatus());
        response.setBranding(portal.getBranding());
        response.setCreatedAt(portal.getCreatedAt());
        response.setUpdatedAt(portal.getUpdatedAt());
        return response;
    }
}
