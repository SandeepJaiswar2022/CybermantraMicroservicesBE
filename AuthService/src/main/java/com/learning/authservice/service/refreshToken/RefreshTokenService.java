package com.learning.authservice.service.refreshToken;

import java.util.UUID;

import com.learning.authservice.dto.UserDto;

public interface RefreshTokenService {
    String createTokenFamily(UUID userId, String role, String email,
            String firstName, String lastName,
            boolean isEmailVerified);

    RotateResult rotateIfValid(String incomingToken);

    void revokeFamily(String familyId);

    public record RotateResult(String newRefreshToken, UserDto user) {
    }

}
