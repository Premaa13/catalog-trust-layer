package com.catalogtrust.model;

import java.time.OffsetDateTime;

public record CatalogSnapshot(
        String productId,
        String productName,
        String category,
        double price,
        double stock,
        OffsetDateTime lastUpdated,
        String dateAdded
) {
    public CatalogSnapshot(
            String productId,
            String productName,
            double price,
            double stock,
            OffsetDateTime lastUpdated
    ) {
        this(productId, productName, null, price, stock, lastUpdated, null);
    }
}
