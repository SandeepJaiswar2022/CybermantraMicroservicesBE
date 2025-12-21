package com.learning.authservice.controller;

import com.learning.authservice.dto.ApiResponse;
import com.learning.authservice.dto.LoginRequest;
import com.learning.authservice.dto.RegisterRequest;
import com.learning.authservice.dto.RegisterResponse;
import com.learning.authservice.exception.ResourceNotFoundException;
import com.learning.authservice.repository.UserRepository;
import com.learning.authservice.service.auth.AuthService;
import com.learning.authservice.service.jwt.JwtService;
import com.learning.authservice.service.refreshToken.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.base-url}/auth")
public class AuthController {
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Value("${jwt.refresh-max-expiry-seconds}")
    private long refreshTokenExpirySeconds;

//    @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE)
@PostMapping(value = "/register")
public ResponseEntity<ApiResponse<Object>> register(
            @RequestBody RegisterRequest req) {

        // 1️⃣ Register user and generate tokens
        RegisterResponse response = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registered Successfully.", response));
    }

    @PostMapping(value="/login")
    public ResponseEntity<ApiResponse<Object>> login(@RequestBody LoginRequest req, HttpServletResponse response) {
        Map<String, Object> loginResponse = authService.login(req);
        // Send refresh token as HttpOnly cookie
        ResponseCookie cookie = ResponseCookie.from("refreshToken", loginResponse.get("refreshToken").toString())
                .httpOnly(true)
                .secure(false) // set true in prod with HTTPS
                .path("/")
                .maxAge(refreshTokenExpirySeconds) // 30 days
                .sameSite("None")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Logged In Successfully.",loginResponse.getOrDefault("authResponse",null)));
    }

//    @PostMapping("/refresh")
//    public ResponseEntity<ApiResponse<Object>> refresh(HttpServletRequest request, HttpServletResponse response) {
//        String refreshToken = null;
//
//        // Extract refresh token from cookies
//        if (request.getCookies() != null) {
//            for (Cookie cookie : request.getCookies()) {
//                if ("refreshToken".equals(cookie.getName())) {
//                    refreshToken = cookie.getValue();
//                    break;
//                }
//            }
//        }
//
//        if (refreshToken == null) {
//            throw new ResourceNotFoundException("Refresh Token not Found!");
//        }
//
//        // rotate in Redis via Lua CAS
////        var res = refreshTokenService.rotateIfValid(refreshToken);
//
//        // rotate successful: new refresh token returned, but we need userId to generate access token
//        // We must obtain userId by reading Redis family key. To simplify, parse familyId and get user_id
//        // 1️⃣ Delegate to service layer (which handles Redis + Lua CAS)
//        var result = refreshTokenService.rotateIfValid(refreshToken);
//
//        // 2️⃣ Extract user snapshot from Redis (no SQL)
//        Map<Object, Object> userData = result.userData();
//        UUID userId = UUID.fromString((String) userData.get("user_id"));
//
//        // 3️⃣ Generate new access token (from cached info)
//        String accessToken = jwtService.generateAccessToken(userId);
//
//        // set new cookie
//        ResponseCookie cookie = ResponseCookie.from("refreshToken", result.newRefreshToken())
//                .httpOnly(true)
//                .secure(false)
//                .path("/")
//                .maxAge(refreshTokenExpirySeconds) // 30 days
//                .sameSite("None")
//                .build();
//        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
//
//
//        Map<String, String> bodyResponse = new HashMap<>();
//        bodyResponse.put("accessToken", accessToken);
//
//        return ResponseEntity.status(HttpStatus.OK)
//                .body(ApiResponse.success("Token Refreshed Successfully.",bodyResponse));
//    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Object>> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = null;

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }

        if (refreshToken == null) {
            throw new ResourceNotFoundException("Refresh Token not Found!");
        }

        // Call the rotate function
        var result = refreshTokenService.rotateIfValid(refreshToken);

        // Extract user info from Redis
        Map<Object, Object> userData = result.userData();
        UUID userId = UUID.fromString((String) userData.get("user_id"));
        String role = (String) userData.get("role");
        String email = (String) userData.get("email");

        // Generate new access token
        String accessToken = jwtService.generateAccessToken(userId,email,role);

        // Set new refresh cookie
        ResponseCookie cookie = ResponseCookie.from("refreshToken", result.newRefreshToken())
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(refreshTokenExpirySeconds)
                .sameSite("None")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Build response for debugging
        Map<String, Object> debugResponse = new LinkedHashMap<>();
        debugResponse.put("accessToken", accessToken);
        debugResponse.put("newRefreshToken", result.newRefreshToken());
        debugResponse.put("tokenFamilyData", userData); // this includes user_id, hashes, timestamps, etc.

        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success("Token rotated successfully (debug mode).", debugResponse));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        try {
            String message = authService.verifyEmail(token);
            return ResponseEntity.ok(message);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
