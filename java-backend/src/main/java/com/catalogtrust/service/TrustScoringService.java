package com.catalogtrust.service;

import com.catalogtrust.model.CatalogSnapshot;
import com.catalogtrust.model.OrderRecord;
import com.catalogtrust.model.TrustScoringResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TrustScoringService {
    private static final String VERIFIED = "VERIFIED";
    private static final String FLAGGED = "FLAGGED";
    private static final String ALLOW = "ALLOW";
    private static final String ALLOW_WITH_WARNING = "ALLOW_WITH_WARNING";
    private static final String REVIEW = "REVIEW";
    private static final String BLOCK = "BLOCK";
    private static final String STALE_OVERRIDE_REASON =
            "SAFETY OVERRIDE: severe staleness forces REVIEW regardless of score";

    public TrustScoringResult evaluateProduct(
            String productId,
            List<CatalogSnapshot> catalog,
            List<OrderRecord> orders
    ) {
        List<CatalogSnapshot> productSnapshots = catalog.stream()
                .filter(row -> row.productId().equals(productId))
                .toList();

        if (productSnapshots.isEmpty()) {
            throw new IllegalArgumentException("product_id not found");
        }

        CatalogSnapshot latestProductSnapshot = latestSnapshot(productSnapshots);
        OffsetDateTime latestDatasetTimestamp = catalog.stream()
                .map(CatalogSnapshot::lastUpdated)
                .max(Comparator.comparing(OffsetDateTime::toInstant))
                .orElseThrow();

        int score = 100;
        List<String> reasons = new ArrayList<>();

        List<CatalogSnapshot> sortedSnapshots = productSnapshots.stream()
                .sorted(Comparator.comparing(
                        (CatalogSnapshot row) -> row.lastUpdated().toInstant()
                ))
                .toList();

        if (sortedSnapshots.size() > 1) {
            CatalogSnapshot previousSnapshot = sortedSnapshots.get(sortedSnapshots.size() - 2);
            CatalogSnapshot newestSnapshot = sortedSnapshots.get(sortedSnapshots.size() - 1);
            double oldPrice = previousSnapshot.price();
            double priceChangePercent = oldPrice != 0
                    ? ((newestSnapshot.price() - oldPrice) / oldPrice) * 100
                    : 0;
            double timeDifferenceHours = hoursBetween(
                    previousSnapshot.lastUpdated(),
                    newestSnapshot.lastUpdated()
            );

            if (priceChangePercent > 50) {
                score -= 50;
                reasons.add("Price increased " + formatNumber(priceChangePercent, 1)
                        + "% in " + formatNumber(timeDifferenceHours, 1) + " hours (high-risk)");
            } else if (priceChangePercent > 25) {
                score -= 25;
                reasons.add("Price increased " + formatNumber(priceChangePercent, 1)
                        + "% in " + formatNumber(timeDifferenceHours, 1) + " hours (suspicious)");
            }
        }

        double claimedStock = latestProductSnapshot.stock();
        OffsetDateTime twoHoursBeforeSnapshot = latestProductSnapshot.lastUpdated().minusHours(2);
        double recentSoldQuantity = orders.stream()
                .filter(order -> order.productId().equals(productId))
                .filter(order -> "completed".equals(order.status()))
                .filter(order -> !order.timestamp().isBefore(twoHoursBeforeSnapshot))
                .filter(order -> !order.timestamp().isAfter(latestProductSnapshot.lastUpdated()))
                .mapToDouble(OrderRecord::quantity)
                .sum();

        if (recentSoldQuantity > claimedStock) {
            score -= 50;
            reasons.add("Claimed stock (" + formatNumber(claimedStock, 0)
                    + ") is less than recently sold quantity (" + formatNumber(recentSoldQuantity, 0)
                    + ") in last 2 hours");
        } else if (recentSoldQuantity > 0 && recentSoldQuantity >= (0.8 * claimedStock)) {
            score -= 20;
            reasons.add("High sales velocity relative to claimed stock");
        }

        double hoursSinceUpdate = hoursBetween(
                latestProductSnapshot.lastUpdated(),
                latestDatasetTimestamp
        );

        if (hoursSinceUpdate > 72) {
            score -= 40;
            reasons.add("Listing stale: not updated in " + formatNumber(hoursSinceUpdate, 1)
                    + " hours (severe)");
        } else if (hoursSinceUpdate > 48) {
            score -= 20;
            reasons.add("Listing stale: not updated in " + formatNumber(hoursSinceUpdate, 1) + " hours");
        } else if (hoursSinceUpdate > 24) {
            score -= 10;
            reasons.add("Listing aging: not updated in " + formatNumber(hoursSinceUpdate, 1)
                    + " hours (warning)");
        }

        score = Math.max(0, score);
        String status;
        String action;

        if (score >= 90) {
            status = VERIFIED;
            action = ALLOW;
        } else if (score >= 60) {
            status = FLAGGED;
            action = ALLOW_WITH_WARNING;
        } else if (score >= 30) {
            status = FLAGGED;
            action = REVIEW;
        } else {
            status = FLAGGED;
            action = BLOCK;
        }

        if (hoursSinceUpdate > 72 && !BLOCK.equals(action)) {
            action = REVIEW;
            status = FLAGGED;
            if (!reasons.contains(STALE_OVERRIDE_REASON)) {
                reasons.add(STALE_OVERRIDE_REASON);
            }
        }

        return new TrustScoringResult(
                productId,
                latestProductSnapshot.productName(),
                score,
                status,
                action,
                reasons
        );
    }

    private CatalogSnapshot latestSnapshot(List<CatalogSnapshot> snapshots) {
        CatalogSnapshot latest = snapshots.get(0);
        for (int index = 1; index < snapshots.size(); index++) {
            CatalogSnapshot candidate = snapshots.get(index);
            if (candidate.lastUpdated().isAfter(latest.lastUpdated())) {
                latest = candidate;
            }
        }
        return latest;
    }

    private double hoursBetween(OffsetDateTime start, OffsetDateTime end) {
        Duration duration = Duration.between(start, end);
        return (duration.getSeconds() + duration.getNano() / 1_000_000_000.0) / 3600.0;
    }

    private String formatNumber(double value, int decimalPlaces) {
        return new BigDecimal(value)
                .setScale(decimalPlaces, RoundingMode.HALF_EVEN)
                .toPlainString();
    }
}
