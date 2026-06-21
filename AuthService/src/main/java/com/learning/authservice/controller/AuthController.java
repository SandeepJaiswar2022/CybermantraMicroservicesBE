package com.learning.authservice.controller;

import com.learning.authservice.dto.ApiResponse;
import com.learning.authservice.dto.AuthResponse;
import com.learning.authservice.dto.LoginRequest;
import com.learning.authservice.dto.RegisterRequest;
import com.learning.authservice.dto.RegisterResponse;
import com.learning.authservice.exception.ResourceNotFoundException;
import com.learning.authservice.service.auth.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("${api.base-url}/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.refresh-max-expiry-seconds}")
    private long refreshTokenMaxAgeSeconds;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@RequestBody RegisterRequest req) {
        RegisterResponse response = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registered successfully.", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody LoginRequest req,
            HttpServletResponse response) {

        AuthService.LoginResult result = authService.login(req); // ← AuthService not AuthServiceImpl

        response.addHeader(HttpHeaders.SET_COOKIE,
                buildRefreshCookie(result.rawRefreshToken()).toString());

        return ResponseEntity.ok(ApiResponse.success("Logged in successfully.", result.authResponse()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshToken = extractRefreshTokenCookie(request);
        AuthService.RefreshResult result = authService.refresh(refreshToken); // ← same

        response.addHeader(HttpHeaders.SET_COOKIE,
                buildRefreshCookie(result.newRefreshToken()).toString());

        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully.", result.authResponse()));
    }

    // Add logout endpoint to AuthController
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        // Revoke the token family in Redis
        String refreshToken = extractRefreshTokenCookie(request);
        authService.logout(refreshToken);

        // Clear the cookie
        ResponseCookie expiredCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0) // ← immediately expires the cookie
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());

        return ResponseEntity.ok(ApiResponse.success("Logged out successfully.", null));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(@RequestParam String token) {
        String message = authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private String extractRefreshTokenCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        throw new ResourceNotFoundException("Refresh token cookie not found.");
    }

    private ResponseCookie buildRefreshCookie(String token) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(false) // → true in production (HTTPS)
                .path("/")
                .maxAge(refreshTokenMaxAgeSeconds)
                .sameSite("Lax") // consistent across login + refresh
                .build();
    }
}
