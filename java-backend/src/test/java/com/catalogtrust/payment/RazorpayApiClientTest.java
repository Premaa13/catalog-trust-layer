package com.catalogtrust.payment;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpMethod.POST;

class RazorpayApiClientTest {
    @Test
    void postsARealisticTestOrderRequestThroughMockServerOnly() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.razorpay.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RazorpayApiClient client = new RazorpayApiClient(builder.build());
        String authorization = "Basic " + Base64.getEncoder()
                .encodeToString("test_key:test_secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        server.expect(requestTo("https://api.razorpay.com/v1/orders"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(jsonPath("$.amount").value(4567))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.receipt").value("trust-layer-lamp"))
                .andRespond(withSuccess("""
                        {"id":"order_test_456","amount":4567,"currency":"INR","status":"created"}
                        """, MediaType.APPLICATION_JSON));

        RazorpayOrderResponse response = client.createOrder("test_key", "test_secret", 4567, "trust-layer-lamp");

        assertEquals("order_test_456", response.orderId());
        assertEquals(4567L, response.amount());
        assertEquals("INR", response.currency());
        assertEquals("created", response.status());
        server.verify();
    }

    @Test
    void reportsProviderHttpErrorsWithoutReturningProviderBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.razorpay.com/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RazorpayApiClient client = new RazorpayApiClient(builder.build());
        server.expect(requestTo("https://api.razorpay.com/v1/orders"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("{\"error\":{\"description\":\"private provider response\"}}"));

        RazorpayPaymentException exception = assertThrows(
                RazorpayPaymentException.class,
                () -> client.createOrder("rzp_test_key", "secret", 100, "trust-layer-test")
        );

        assertEquals("Razorpay API returned HTTP 503", exception.getMessage());
        assertFalse(exception.getMessage().contains("private"));
        server.verify();
    }

    @Test
    void reportsProviderTimeoutWithoutExposingRequestDetails() {
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec request = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(restClient.post()).thenReturn(request);
        when(request.uri("/orders")).thenReturn(request);
        when(request.headers(org.mockito.ArgumentMatchers.<Consumer<HttpHeaders>>any())).thenReturn(request);
        when(request.body(any(Object.class))).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.onStatus(
                org.mockito.ArgumentMatchers.<Predicate<org.springframework.http.HttpStatusCode>>any(),
                org.mockito.ArgumentMatchers.<RestClient.ResponseSpec.ErrorHandler>any()
        )).thenReturn(response);
        when(response.body(RazorpayOrderResponse.class))
                .thenThrow(new ResourceAccessException(
                        "request failed",
                        new SocketTimeoutException("private network detail")
                ));
        RazorpayApiClient client = new RazorpayApiClient(restClient);

        RazorpayPaymentException exception = assertThrows(
                RazorpayPaymentException.class,
                () -> client.createOrder("rzp_test_example", "fake_secret", 100, "receipt")
        );

        assertEquals("Razorpay request timed out after 15 seconds.", exception.getMessage());
        assertFalse(exception.getMessage().contains("private"));
        assertFalse(exception.getMessage().contains("fake_secret"));
    }
}
