package com.fincore.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Successful authentication token response")
public class AuthResponse {

    @Schema(description = "JWT Bearer access token for authenticating subsequent API requests", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "Opaque cryptographically secure refresh token for session continuation", example = "3c983a47-c0e6-42d4-b903-ea25595304b4")
    private String refreshToken;

    @Schema(description = "Token authentication scheme", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Access token lifespan in seconds", example = "900")
    private long expiresIn;

    @Schema(description = "Profile information of the authenticated user")
    private UserProfileResponse user;

    public AuthResponse() {}

    public AuthResponse(String accessToken, String refreshToken, long expiresIn, UserProfileResponse user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
        this.user = user;
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

    public UserProfileResponse getUser() {
        return user;
    }

    public void setUser(UserProfileResponse user) {
        this.user = user;
    }
}

