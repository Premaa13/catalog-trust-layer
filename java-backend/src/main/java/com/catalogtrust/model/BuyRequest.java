package com.catalogtrust.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The storefront sends only the ID; the backend obtains price from the catalog.
 */
public record BuyRequest(@JsonProperty("product_id") String productId) {
}
