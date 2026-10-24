package com.catalogtrust.agent;

import java.math.BigDecimal;

/**
 * The searchable words and optional price ceiling extracted from a request.
 */
public record ShoppingIntent(String keyword, BigDecimal maxPrice) {
}
