package com.catalogtrust.model;

import java.util.List;

public record TrustScoringResult(
        String productId,
        String productName,
        int trustScore,
        String status,
        String action,
        List<String> reasons
) {
    public TrustScoringResult {
        reasons = List.copyOf(reasons);
    }
}
