package com.changelog.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlugServiceTest {

    private final SlugService slugService = new SlugService();

    @Test
    void shouldSlugifyBasicString() {
        assertEquals("my-awesome-product", slugService.slugify("My Awesome Product!"));
    }

    @Test
    void shouldHandleMultipleHyphens() {
        assertEquals("my-product", slugService.slugify("My   Product!!!"));
    }

    @Test
    void shouldRemoveLeadingAndTrailingHyphens() {
        assertEquals("product-name", slugService.slugify("-Product Name-"));
    }

    @Test
    void shouldThrowExceptionForEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> slugService.slugify(""));
        assertThrows(IllegalArgumentException.class, () -> slugService.slugify(null));
    }

    @Test
    void shouldValidateValidSlugs() {
        assertTrue(slugService.isValidSlug("valid-slug-123"));
        assertFalse(slugService.isValidSlug("Invalid Slug"));
        assertFalse(slugService.isValidSlug("invalid--slug"));
        assertFalse(slugService.isValidSlug("-invalid-"));
    }
}
