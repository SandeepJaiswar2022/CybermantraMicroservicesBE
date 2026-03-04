package com.mobisec.in.userservice.controller;

import com.mobisec.in.userservice.dto.ApiResponse;
import com.mobisec.in.userservice.dto.UpdateProfileRequest;
import com.mobisec.in.userservice.dto.UserProfileResponse;

// ============================================================================
// controller/UserController.java
// ============================================================================

import com.mobisec.in.userservice.entity.UserProfile;
import com.mobisec.in.userservice.service.UserService.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("${api.base-url}/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * GET /me — returns own profile.
     * Any authenticated user (STUDENT, INSTRUCTOR, ADMIN).
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("userId");

        log.info("GET /me - userId: {}", userId);

        UserProfileResponse response = userService.getMyProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("Profile fetched", response));
    }

    /**
     * GET /{userId} — fetch profile by userId.
     * STUDENT/INSTRUCTOR: own profile only.
     * ADMIN: any profile.
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserById(
            @PathVariable UUID userId,
            HttpServletRequest request) {

        UUID requestingUserId = (UUID) request.getAttribute("userId");
        String requestingUserRole = (String) request.getAttribute("userRole");

        log.info("GET /{} - requestingUserId: {}, role: {}", userId, requestingUserId, requestingUserRole);

        UserProfileResponse response = userService.getUserById(requestingUserId, requestingUserRole, userId);
        return ResponseEntity.ok(ApiResponse.success("User fetched", response));
    }

    /**
     * PUT /me — update own profile.
     * Any authenticated user.
     */
    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest updateRequest,
            HttpServletRequest request) {

        UUID userId = (UUID) request.getAttribute("userId");

        log.info("PUT /me - userId: {}", userId);

        UserProfileResponse response = userService.updateMyProfile(userId, updateRequest);
        return ResponseEntity.ok(ApiResponse.success("Profile updated", response));
    }

    /**
     * GET / — list all users.
     * ADMIN only.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> getAllUsers() {
        log.info("GET /users - admin request");

        List<UserProfileResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success("Users fetched", users));
    }
}