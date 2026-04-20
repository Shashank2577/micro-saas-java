package com.changelog.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class SlugService {

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    /**
     * Normalizes a string to a URL-safe slug.
     * Example: "My Awesome Product!" -> "my-awesome-product"
     */
    public String slugify(String input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }

        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        
        return slug.toLowerCase(Locale.ENGLISH)
                .replaceAll("-{2,}", "-") // Remove multiple consecutive hyphens
                .replaceAll("^-", "")      // Remove leading hyphen
                .replaceAll("-$", "");     // Remove trailing hyphen
    }

    /**
     * Validates if a string is already a valid slug.
     */
    public boolean isValidSlug(String slug) {
        if (slug == null || slug.isEmpty()) return false;
        return Pattern.matches("^[a-z0-9]+(?:-[a-z0-9]+)*$", slug);
    }
}
