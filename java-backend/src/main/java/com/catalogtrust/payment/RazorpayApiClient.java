package com.catalogtrust.payment;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Sends order-creation requests to Razorpay's test-safe Orders API.
 */
@Component
public class RazorpayApiClient {
    static final int TIMEOUT_SECONDS = 15;

    private final RestClient restClient;

    public RazorpayApiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public RazorpayOrderResponse createOrder(
            String keyId,
            String keySecret,
            long amountPaise,
            String receipt
    ) {
        try {
            return restClient.post()
                    .uri("/orders")
                    .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                    .body(Map.of(
                            "amount", amountPaise,
                            "currency", "INR",
                            "receipt", receipt
                    ))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new RazorpayPaymentException(
                                "Razorpay API returned HTTP " + response.getStatusCode().value()
                        );
                    })
                    .body(RazorpayOrderResponse.class);
        } catch (RazorpayPaymentException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw new RazorpayPaymentException(
                    "Razorpay API returned HTTP " + exception.getStatusCode().value()
            );
        } catch (ResourceAccessException exception) {
            if (containsTimeout(exception)) {
                throw new RazorpayPaymentException(
                        "Razorpay request timed out after " + TIMEOUT_SECONDS + " seconds.",
                        exception
                );
            }
            throw new RazorpayPaymentException("Razorpay request failed.", exception);
        } catch (RestClientException exception) {
            throw new RazorpayPaymentException("Razorpay request failed.", exception);
        }
    }

    private boolean containsTimeout(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
