package com.learning.authservice.service.Auth;


import com.learning.authservice.Enum.Role;
import com.learning.authservice.dto.AuthResponse;
import com.learning.authservice.dto.LoginRequest;
import com.learning.authservice.dto.RegisterRequest;
import com.learning.authservice.entity.User;
import com.learning.authservice.exception.AlreadyExistException;
import com.learning.authservice.repository.UserRepository;
import com.learning.authservice.service.jwt.JwtService;
import com.learning.authservice.service.refreshToken.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * High-level auth orchestration: register, login, logout, refresh (delegates to RefreshTokenService).
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Override
    public Map<String, Object> register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new AlreadyExistException("Email already exist!");
        }
        var role = Role.valueOf("STUDENT");
        User user = User.builder()
                .email(req.getEmail())
                .fullName(req.getFullName())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(role)
                .enabled(true)
                .build();
        userRepository.save(user);

        // Create token family immediately (auto-login)
        String refreshToken = refreshTokenService.createTokenFamily(user.getId());
        return getAuthResponseAndRefreshToken(user, refreshToken);
    }

    private Map<String, Object> getAuthResponseAndRefreshToken(User user, String refreshToken) {
        String accessToken = jwtService.generateAccessToken(user.getId());

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
        Map<String, Object> result = new HashMap<>();
        result.put("authResponse", authResponse);
        result.put("refreshToken", refreshToken);

        return result;
    }

    public Map<String, Object> login(LoginRequest req) {
        // authenticate
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
        User user = (User) auth.getPrincipal();

        // create token family
        String refreshToken = refreshTokenService.createTokenFamily(user.getId());
        return getAuthResponseAndRefreshToken(user, refreshToken);
    }

}