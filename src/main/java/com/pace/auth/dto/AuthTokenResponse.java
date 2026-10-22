package com.pace.auth.dto;

public record AuthTokenResponse(String accessToken, String refreshToken, int accessTokenExpiresIn,
                                LoginUserResponse user, boolean isNewUser) {}
