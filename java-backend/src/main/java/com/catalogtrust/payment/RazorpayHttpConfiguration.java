package com.catalogtrust.payment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RazorpayHttpConfiguration {
    private static final String RAZORPAY_API_BASE_URL = "https://api.razorpay.com/v1";

    @Bean
    public RestClient razorpayRestClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(RazorpayApiClient.TIMEOUT_SECONDS))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(RazorpayApiClient.TIMEOUT_SECONDS));

        return builder
                .baseUrl(RAZORPAY_API_BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }
}
