package com.changelog.business.acquisition.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLandingPageRequest {
    private String name;
    private String slug;
    private String primaryDomain;
    private List<LandingVariantRequest> variants;
    private Boolean autoABTest;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LandingVariantRequest {
        private String name;
        private String headline;
        private String subheadline;
        private String ctaText;
        private String ctaLink;
        private String bodyContent;
        private String logoUrl;
        private String heroImageUrl;
        private String testimonial;
        private List<Feature> features;
        private String metaTitle;
        private String metaDescription;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class Feature {
            private String name;
            private String description;
            private String icon;
        }
    }
}
