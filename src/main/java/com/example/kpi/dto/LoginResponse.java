package com.example.kpi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("token_type")
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * Remaining lifetime of the access token, in <b>seconds</b>, per RFC 6749.
     * Not milliseconds — it was, and a client scheduling a refresh from it
     * would have waited long past expiry.
     */
    @JsonProperty("expires_in")
    private Long expiresIn;

    private UserResponse user;
}

