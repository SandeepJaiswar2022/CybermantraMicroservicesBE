package com.learning.authservice.service.auth;

import com.learning.authservice.dto.AuthResponse;
import com.learning.authservice.dto.LoginRequest;
import com.learning.authservice.dto.RegisterRequest;
import com.learning.authservice.dto.RegisterResponse;

public interface AuthService {
    RegisterResponse register(RegisterRequest req);

    LoginResult login(LoginRequest req);

    RefreshResult refresh(String incomingRefreshToken);

    void logout(String refreshToken);

    String verifyEmail(String token);

    // Define records on the interface — controller depends only on this
    record LoginResult(AuthResponse authResponse, String rawRefreshToken) {
    }

    record RefreshResult(AuthResponse authResponse, String newRefreshToken) {
    }
}
