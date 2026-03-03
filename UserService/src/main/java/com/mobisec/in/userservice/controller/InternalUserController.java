package com.mobisec.in.userservice.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mobisec.in.userservice.dto.ApiResponse;
import com.mobisec.in.userservice.dto.InternalUserVerifyResponse;
import com.mobisec.in.userservice.service.UserService.UserService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("${api.base-url}/internal/users")
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private final UserService userService;

    /**
     * GET /internal/users/{userId}/verify
     *
     * Called ONLY by internal services — requires ROLE_SERVICE.
     * User tokens (INSTRUCTOR, ADMIN, STUDENT) are rejected by @PreAuthorize.
     *
     * @param userId       the user to verify
     * @param expectedRole optional — if provided, server asserts role match
     */
    @GetMapping("/{userId}/verify")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<ApiResponse<InternalUserVerifyResponse>> verifyUser(
            @PathVariable UUID userId,
            @RequestParam(required = false) String expectedRole,
            HttpServletRequest request) {

        // Log which service is making this call for audit trail
        String callerService = (String) request.getAttribute("callerServiceName");
        log.info("Internal verify request - userId: {}, expectedRole: {}, calledBy: {}",
                userId, expectedRole, callerService);

        InternalUserVerifyResponse response = userService.verifyUserForService(userId, expectedRole);

        return ResponseEntity.ok(ApiResponse.success("User verified", response));
    }
}