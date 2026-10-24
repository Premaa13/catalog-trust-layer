package com.catalogtrust.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The fields returned by Razorpay when a test order is created.
 */
public record RazorpayOrderResponse(
        @JsonProperty("id") String orderId,
        Long amount,
        String currency,
        String status
) {
}
