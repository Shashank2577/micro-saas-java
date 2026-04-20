package com.changelog.business.monetization.repository;

import com.changelog.business.monetization.model.StripeProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StripeProductRepository extends JpaRepository<StripeProduct, UUID> {

    List<StripeProduct> findByTenantId(UUID tenantId);

    List<StripeProduct> findByTenantIdAndActive(UUID tenantId, Boolean active);
}
