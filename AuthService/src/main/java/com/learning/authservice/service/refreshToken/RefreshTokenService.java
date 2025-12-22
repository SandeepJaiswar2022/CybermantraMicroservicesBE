package com.learning.authservice.service.refreshToken;

import java.util.UUID;

public interface RefreshTokenService {
    String createTokenFamily(UUID userId, String role);
    RefreshTokenServiceImpl.RotateResult rotateIfValid(String incomingToken);
    void revokeFamily(String familyId);

}
