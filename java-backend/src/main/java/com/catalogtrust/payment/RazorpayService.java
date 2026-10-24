package com.catalogtrust.payment;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Validates INR amounts and creates Razorpay orders in test mode only.
 */
@Service
public class RazorpayService {
    private static final DateTimeFormatter RECEIPT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSSSS", Locale.ROOT);

    private final RazorpayApiClient apiClient;
    private final RazorpayCredentials credentials;
    private final Clock clock;

    @Autowired
    public RazorpayService(RazorpayApiClient apiClient, RazorpayCredentials credentials) {
        this(apiClient, credentials, Clock.systemUTC());
    }

    RazorpayService(RazorpayApiClient apiClient, RazorpayCredentials credentials, Clock clock) {
        this.apiClient = apiClient;
        this.credentials = credentials;
        this.clock = clock;
    }

    public RazorpayOrderResponse createTestOrder(String productName, BigDecimal amountInr) {
        if (amountInr == null || amountInr.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0 INR.");
        }
        long amountPaise;
        try {
            amountPaise = amountInr.movePointRight(2)
                    .setScale(0, RoundingMode.HALF_EVEN)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Amount is too large to create a Razorpay order.", exception);
        }
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("Amount must be at least 0.01 INR.");
        }

        RazorpayCredentials.Credentials configuredCredentials;
        try {
            configuredCredentials = credentials.requireCredentials();
        } catch (IllegalStateException exception) {
            throw new RazorpayPaymentException(exception.getMessage(), exception);
        }

        String receipt = "trust-layer-" + sanitizeReceiptName(productName) + "-"
                + LocalDateTime.now(clock.withZone(ZoneOffset.UTC)).format(RECEIPT_TIMESTAMP);
        RazorpayOrderResponse response = apiClient.createOrder(
                configuredCredentials.keyId(),
                configuredCredentials.keySecret(),
                amountPaise,
                receipt
        );
        if (response == null) {
            throw new RazorpayPaymentException("Razorpay returned an empty order response.");
        }
        return response;
    }

    private String sanitizeReceiptName(String productName) {
        if (productName == null || productName.isBlank()) {
            return "product";
        }
        String normalized = Normalizer.normalize(productName.toLowerCase(Locale.ROOT).strip(), Normalizer.Form.NFKD);
        StringBuilder safeName = new StringBuilder();
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint) || codePoint == '-' || codePoint == '_') {
                safeName.appendCodePoint(codePoint);
            } else {
                safeName.append('-');
            }
            offset += Character.charCount(codePoint);
        }
        return safeName.isEmpty() ? "product" : safeName.toString();
    }
}
