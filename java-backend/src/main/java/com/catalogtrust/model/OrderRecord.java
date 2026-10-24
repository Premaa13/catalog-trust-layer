package com.catalogtrust.model;

import java.time.OffsetDateTime;

public record OrderRecord(
        String orderId,
        String productId,
        double quantity,
        OffsetDateTime timestamp,
        String status
) {
    public OrderRecord(String productId, double quantity, OffsetDateTime timestamp, String status) {
        this(null, productId, quantity, timestamp, status);
    }
}
