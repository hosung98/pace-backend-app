package com.pace.auth.service;

import com.pace.auth.dto.AuthTokenResponse;

public interface AuthService {
    AuthTokenResponse login(String idToken);
    AuthTokenResponse loginApple(String idToken, String nonce, String nickname);
    AuthTokenResponse refresh(String refreshToken);
    void logout(String publicId, String refreshToken);
}
