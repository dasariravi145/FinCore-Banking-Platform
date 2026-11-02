package com.fincore.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Session termination and token revocation request payload")
public class LogoutRequest {

    @Schema(description = "Refresh token to revoke (optional if user already authenticated via Bearer)", example = "d94b15c9-94b1-4f18-bb73-9a3b663b6521")
    private String refreshToken;

    public LogoutRequest() {}

    public LogoutRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}

