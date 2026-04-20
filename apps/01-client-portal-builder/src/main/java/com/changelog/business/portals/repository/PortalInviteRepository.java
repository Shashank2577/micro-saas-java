package com.changelog.business.portals.repository;

import com.changelog.business.portals.model.PortalInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortalInviteRepository extends JpaRepository<PortalInvite, UUID> {
    Optional<PortalInvite> findByToken(String token);
}
