package com.changelog.service;

import com.changelog.model.Space;
import com.changelog.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceRepository spaceRepository;

    @Transactional(readOnly = true)
    public List<Space> getSpaces(UUID tenantId) {
        return spaceRepository.findByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public Optional<Space> getSpace(UUID tenantId, UUID spaceId) {
        return spaceRepository.findByIdAndTenantId(spaceId, tenantId);
    }

    @Transactional
    public Space createSpace(UUID tenantId, Space space) {
        space.setTenantId(tenantId);
        return spaceRepository.save(space);
    }
}