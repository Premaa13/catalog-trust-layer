package com.catalogtrust.agent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShoppingIntentParserTest {
    private final ShoppingIntentParser parser = new ShoppingIntentParser();

    @Test
    void extractsProductKeywordAndPriceCeiling() {
        ShoppingIntent intent = parser.parse("Please find good wireless earbuds under ₹15,000");

        assertEquals("wireless earbuds", intent.keyword());
        assertEquals(new BigDecimal("15000"), intent.maxPrice());
    }

    @Test
    void parsesPriceSuffixedByCurrencyAndFallsBackForAmbiguousRequest() {
        ShoppingIntent currencyPrice = parser.parse("show me lamps 2,500 rupees");
        ShoppingIntent ambiguous = parser.parse("cheap under 100");

        assertEquals("lamps", currencyPrice.keyword());
        assertEquals(new BigDecimal("2500"), currencyPrice.maxPrice());
        assertEquals("product", ambiguous.keyword());
        assertEquals(new BigDecimal("100"), ambiguous.maxPrice());
    }
}
