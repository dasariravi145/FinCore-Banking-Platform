package com.fincore.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token refresh rotation response")
public class TokenRefreshResponse {

    @Schema(description = "Newly issued JWT Bearer access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "Newly rotated opaque refresh token", example = "71f76d91-fa89-40ad-be87-57bc1de15b91")
    private String refreshToken;

    @Schema(description = "Token authentication scheme", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Access token lifespan in seconds", example = "900")
    private long expiresIn;

    public TokenRefreshResponse() {}

    public TokenRefreshResponse(String accessToken, String refreshToken, long expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }
}

