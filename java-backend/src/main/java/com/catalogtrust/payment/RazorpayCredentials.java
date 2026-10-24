package com.catalogtrust.payment;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Reads Razorpay credentials from environment-backed Spring properties.
 */
@Component
public class RazorpayCredentials {
    private final Environment environment;

    public RazorpayCredentials(Environment environment) {
        this.environment = environment;
    }

    public Credentials requireCredentials() {
        String keyId = environment.getProperty("RAZORPAY_KEY_ID");
        String keySecret = environment.getProperty("RAZORPAY_KEY_SECRET");
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new IllegalStateException(
                    "RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET must be set in the environment."
            );
        }
        if (!keyId.startsWith("rzp_test_")) {
            throw new IllegalStateException(
                    "Only test-mode Razorpay credentials are allowed; RAZORPAY_KEY_ID must start with rzp_test_."
            );
        }
        return new Credentials(keyId, keySecret);
    }

    public record Credentials(String keyId, String keySecret) {
    }
}
