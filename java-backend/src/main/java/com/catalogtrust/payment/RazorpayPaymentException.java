package com.catalogtrust.payment;

/**
 * Describes a safe-to-return payment integration error without credentials or response bodies.
 */
public class RazorpayPaymentException extends RuntimeException {
    public RazorpayPaymentException(String message) {
        super(message);
    }

    public RazorpayPaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
