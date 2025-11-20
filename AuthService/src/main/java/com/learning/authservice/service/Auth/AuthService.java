package com.learning.authservice.service.Auth;

import com.learning.authservice.dto.AuthResponse;
import com.learning.authservice.dto.LoginRequest;
import com.learning.authservice.dto.RegisterRequest;

import java.util.Map;

public interface AuthService {
    Map<String, Object> register(RegisterRequest req);
    Map<String, Object> login(LoginRequest req);
}
