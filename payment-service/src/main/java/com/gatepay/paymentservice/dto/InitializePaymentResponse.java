package com.gatepay.paymentservice.dto.paystack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitializePaymentResponse {

    private boolean status;
    private String message;
    private DataObject data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataObject {
        @JsonProperty("authorization_url")  // ✅ Maps authorization_url from API to authorizationUrl in Java
        private String authorizationUrl;

        @JsonProperty("access_code")  // ✅ Maps access_code from API to accessCode in Java
        private String accessCode;

        private String reference;
    }
}