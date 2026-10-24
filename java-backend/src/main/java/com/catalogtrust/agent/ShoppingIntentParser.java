package com.catalogtrust.agent;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts a product keyword and optional INR ceiling from plain shopping text.
 */
@Component
public class ShoppingIntentParser {
    private static final String NUMBER = "(\\d+(?:,\\d{3})*(?:\\.\\d+)?)";
    private static final Pattern[] PRICE_PATTERNS = {
            Pattern.compile("(?:under|below|less\\s+than|within|upto|up\\s+to)\\s*₹?\\s*" + NUMBER,
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile(NUMBER + "\\s*(?:rupees?|inr|rs)", Pattern.CASE_INSENSITIVE)
    };
    private static final Pattern PRICE_PHRASE = Pattern.compile(
            "(?:under|below|less\\s+than|within|upto|up\\s+to)\\s*₹?\\s*"
                    + "\\d+(?:,\\d{3})*(?:\\.\\d+)?\\s*(?:rupees?|inr|rs)?"
                    + "|\\d+(?:,\\d{3})*(?:\\.\\d+)?\\s*(?:rupees?|inr|rs)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-zA-Z0-9\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Set<String> FILLER_WORDS = Set.of(
            "find", "me", "a", "an", "i", "need", "want", "cheap", "show", "looking",
            "for", "buy", "please", "the", "my", "shop", "get", "search", "best",
            "good", "decent", "budget", "under", "below", "less", "than", "within",
            "up", "to", "inr", "rs", "rupees", "price", "max", "with", "without"
    );

    public ShoppingIntent parse(String userQuery) {
        String text = userQuery == null ? "" : userQuery.strip();
        if (text.isEmpty()) {
            return new ShoppingIntent("", null);
        }

        BigDecimal maxPrice = extractPrice(text);
        String remaining = maxPrice == null ? text : PRICE_PHRASE.matcher(text).replaceAll(" ");
        remaining = WHITESPACE.matcher(NON_ALPHANUMERIC.matcher(remaining).replaceAll(" "))
                .replaceAll(" ")
                .strip()
                .toLowerCase();
        String keyword = Arrays.stream(remaining.split(" "))
                .filter(token -> !token.isBlank() && !FILLER_WORDS.contains(token))
                .collect(Collectors.joining(" "));
        if (keyword.isBlank()) {
            keyword = "product";
        }
        return new ShoppingIntent(keyword, maxPrice);
    }

    private BigDecimal extractPrice(String text) {
        for (Pattern pattern : PRICE_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String number = matcher.group(1).replace(",", "");
                return new BigDecimal(number);
            }
        }
        return null;
    }
}
