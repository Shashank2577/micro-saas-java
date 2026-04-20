package com.changelog.business.acquisition.service;

import com.changelog.business.acquisition.dto.CreateLandingPageRequest;
import com.changelog.business.acquisition.model.LandingPage;
import com.changelog.business.acquisition.model.LandingVariant;
import com.changelog.business.acquisition.repository.LandingPageRepository;
import com.changelog.business.acquisition.repository.LandingVariantRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LandingPageService {

    private final LandingPageRepository landingPageRepository;
    private final LandingVariantRepository landingVariantRepository;
    // private final BusinessEventPublisher eventPublisher;

    public List<LandingPage> getAllPages(UUID tenantId) {
        return landingPageRepository.findByTenantId(tenantId);
    }

    public LandingPage getPage(UUID tenantId, UUID pageId) {
        LandingPage page = landingPageRepository.findById(pageId)
                .orElseThrow(() -> new EntityNotFoundException("Landing page not found"));

        if (!page.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Access denied");
        }

        return page;
    }

    @Transactional
    public LandingPage createPage(UUID tenantId, CreateLandingPageRequest request) {
        if (landingPageRepository.existsByTenantIdAndSlug(tenantId, request.getSlug())) {
            throw new IllegalArgumentException("Landing page slug already exists");
        }

        LandingPage page = LandingPage.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .slug(request.getSlug())
                .primaryDomain(request.getPrimaryDomain())
                .platformHosted(generatePlatformHostedUrl(request.getSlug()))
                .status("draft")
                .build();

        LandingPage savedPage = landingPageRepository.save(page);

        // Create variants
        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            for (CreateLandingPageRequest.LandingVariantRequest variantRequest : request.getVariants()) {
                createVariant(savedPage, variantRequest, request.getVariants().indexOf(variantRequest) == 0);
            }
        }

        log.info("Created landing page: tenant={}, page={}, variants={}",
                tenantId, savedPage.getId(), request.getVariants().size());

        return savedPage;
    }

    @Transactional
    public LandingPage activatePage(UUID tenantId, UUID pageId) {
        LandingPage page = getPage(tenantId, pageId);
        page.setStatus("active");

        LandingPage activated = landingPageRepository.save(page);

        // Publish event
        // eventPublisher.publish(BusinessEventType.LANDING_PAGE_PUBLISHED, tenantId, Map.of("pageId", pageId));

        return activated;
    }

    @Transactional
    public LandingVariant createVariant(UUID tenantId, UUID pageId, CreateLandingPageRequest.LandingVariantRequest request) {
        LandingPage page = getPage(tenantId, pageId);
        return createVariant(page, request, false);
    }

    @Transactional
    public void recordView(UUID variantId) {
        landingVariantRepository.findById(variantId)
                .ifPresent(variant -> {
                    variant.setVisitors((variant.getVisitors() != null ? variant.getVisitors() : 0) + 1);
                    landingVariantRepository.save(variant);
                });
    }

    @Transactional
    public void recordConversion(UUID variantId) {
        landingVariantRepository.findById(variantId)
                .ifPresent(variant -> {
                    variant.setConversions((variant.getConversions() != null ? variant.getConversions() : 0) + 1);
                    variant.setConversionRate(
                            (variant.getConversions() * 100.0) / Math.max(1, variant.getVisitors())
                    );
                    landingVariantRepository.save(variant);

                    // Publish conversion event
                    // eventPublisher.publish(BusinessEventType.LANDING_PAGE_CONVERTED, variant.getTenantId(), Map.of(...));
                });
    }

    private LandingVariant createVariant(LandingPage page, CreateLandingPageRequest.LandingVariantRequest request, boolean isControl) {
        LandingVariant variant = LandingVariant.builder()
                .pageId(page.getId())
                .tenantId(page.getTenantId())
                .name(request.getName())
                .isControl(isControl)
                .trafficSplit(isControl ? 50 : 50)
                .status("active")
                .headline(request.getHeadline())
                .subheadline(request.getSubheadline())
                .ctaText(request.getCtaText())
                .ctaLink(request.getCtaLink())
                .bodyContent(request.getBodyContent())
                .logoUrl(request.getLogoUrl())
                .heroImageUrl(request.getHeroImageUrl())
                .testimonial(request.getTestimonial())
                .features(request.getFeatures())
                .metaTitle(request.getMetaTitle())
                .metaDescription(request.getMetaDescription())
                .visitors(0)
                .conversions(0)
                .conversionRate(0.0)
                .build();

        return landingVariantRepository.save(variant);
    }

    private String generatePlatformHostedUrl(String slug) {
        return String.format("https://%s.saasops.io", slug);
    }
}
