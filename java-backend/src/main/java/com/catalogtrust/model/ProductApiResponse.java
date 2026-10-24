package com.catalogtrust.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Product data shared by the dashboard and storefront frontends.
 */
public record ProductApiResponse(
        @JsonProperty("product_id")
        String productId,
        @JsonProperty("product_name")
        String productName,
        @JsonProperty("trust_score")
        int trustScore,
        String status,
        String action,
        List<String> reasons,
        @JsonProperty("is_test_case")
        boolean isTestCase,
        String category,
        @JsonProperty("date_added")
        String dateAdded,
        double price,
        int stock,
        @JsonProperty("image_url")
        String imageUrl
) {
}
