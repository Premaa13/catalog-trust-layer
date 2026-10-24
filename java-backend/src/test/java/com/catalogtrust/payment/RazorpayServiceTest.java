package com.catalogtrust.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RazorpayServiceTest {
    private RazorpayApiClient apiClient;
    private RazorpayCredentials credentials;
    private Environment environment;
    private RazorpayService service;

    @BeforeEach
    void setUp() {
        apiClient = mock(RazorpayApiClient.class);
        environment = mock(Environment.class);
        credentials = new RazorpayCredentials(environment);
        service = new RazorpayService(apiClient, credentials);
    }

    @Test
    void createsTestOrderUsingPaiseAndSanitizedReceipt() {
        when(environment.getProperty("RAZORPAY_KEY_ID")).thenReturn("rzp_test_key_id");
        when(environment.getProperty("RAZORPAY_KEY_SECRET")).thenReturn("test_key_secret");
        when(apiClient.createOrder(eq("rzp_test_key_id"), eq("test_key_secret"), eq(1235L), anyString()))
                .thenReturn(new RazorpayOrderResponse("order_test_123", 1235L, "INR", "created"));

        RazorpayOrderResponse response = service.createTestOrder("  Wireless Earbuds! ", new BigDecimal("12.346"));

        assertEquals("order_test_123", response.orderId());
        assertEquals(1235L, response.amount());
        assertEquals("INR", response.currency());
        assertEquals("created", response.status());
        verify(apiClient).createOrder(
                eq("rzp_test_key_id"),
                eq("test_key_secret"),
                eq(1235L),
                org.mockito.ArgumentMatchers.matches("trust-layer-wireless-earbuds--\\d{20}")
        );
    }

    @Test
    void rejectsMissingCredentialsWithoutCallingRazorpay() {
        when(environment.getProperty("RAZORPAY_KEY_ID")).thenReturn(null);
        when(environment.getProperty("RAZORPAY_KEY_SECRET")).thenReturn(null);

        RazorpayPaymentException exception = assertThrows(
                RazorpayPaymentException.class,
                () -> service.createTestOrder("Lamp", new BigDecimal("1.00"))
        );

        assertEquals(
                "RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET must be set in the environment.",
                exception.getMessage()
        );
        verify(apiClient, never()).createOrder(anyString(), anyString(), eq(100L), anyString());
    }

    @Test
    void refusesLiveModeCredentials() {
        when(environment.getProperty("RAZORPAY_KEY_ID")).thenReturn("rzp_live_key_id");
        when(environment.getProperty("RAZORPAY_KEY_SECRET")).thenReturn("live_secret");

        RazorpayPaymentException exception = assertThrows(
                RazorpayPaymentException.class,
                () -> service.createTestOrder("Lamp", new BigDecimal("1.00"))
        );

        assertEquals(
                "Only test-mode Razorpay credentials are allowed; RAZORPAY_KEY_ID must start with rzp_test_.",
                exception.getMessage()
        );
        verify(apiClient, never()).createOrder(anyString(), anyString(), eq(100L), anyString());
    }

    @Test
    void rejectsInvalidAmountsWithoutCallingRazorpay() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createTestOrder("Lamp", new BigDecimal("0")));
        assertThrows(IllegalArgumentException.class,
                () -> service.createTestOrder("Lamp", new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> service.createTestOrder("Lamp", new BigDecimal("0.001")));
        assertThrows(IllegalArgumentException.class,
                () -> service.createTestOrder("Lamp", null));

        verify(apiClient, never()).createOrder(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(),
                anyString());
    }

    @Test
    void reportsProviderFailureWithoutLeakingCredentialsOrResponseBody() {
        when(environment.getProperty("RAZORPAY_KEY_ID")).thenReturn("rzp_test_hidden_key");
        when(environment.getProperty("RAZORPAY_KEY_SECRET")).thenReturn("hidden_secret");
        when(apiClient.createOrder(eq("rzp_test_hidden_key"), eq("hidden_secret"), eq(100L), anyString()))
                .thenThrow(new RazorpayPaymentException("Razorpay API returned HTTP 503"));

        RazorpayPaymentException exception = assertThrows(
                RazorpayPaymentException.class,
                () -> service.createTestOrder("Lamp", new BigDecimal("1.00"))
        );

        org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains("hidden"));
        assertEquals("Razorpay API returned HTTP 503", exception.getMessage());
    }
}
