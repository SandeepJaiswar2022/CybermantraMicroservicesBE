package com.learning.authservice.service.auth;

import com.learning.authservice.dto.LoginRequest;
import com.learning.authservice.dto.RegisterRequest;
import com.learning.authservice.dto.RegisterResponse;

import java.util.Map;

public interface AuthService {
    RegisterResponse register(RegisterRequest req);
    Map<String, Object> login(LoginRequest req);
    String verifyEmail(String token);
}
