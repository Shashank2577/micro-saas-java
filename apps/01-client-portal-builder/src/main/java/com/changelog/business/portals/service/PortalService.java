package com.changelog.business.portals.service;

import com.changelog.business.portals.model.Portal;
import com.changelog.business.portals.repository.PortalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PortalService {

    private final PortalRepository portalRepository;

    public List<Portal> getPortals(UUID tenantId) {
        return portalRepository.findByTenantId(tenantId);
    }

    public Optional<Portal> getPortal(UUID id, UUID tenantId) {
        return portalRepository.findByIdAndTenantId(id, tenantId);
    }

    public Portal createPortal(Portal portal) {
        return portalRepository.save(portal);
    }

    public Portal updatePortal(Portal portal) {
        return portalRepository.save(portal);
    }

    public void archivePortal(UUID id, UUID tenantId) {
        portalRepository.findByIdAndTenantId(id, tenantId).ifPresent(portal -> {
            portal.setStatus("archived");
            portalRepository.save(portal);
        });
    }
}
