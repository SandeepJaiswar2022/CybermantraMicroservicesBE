package com.mobisec.in.userservice.service.UserService;

import com.mobisec.in.userservice.dto.InternalUserVerifyResponse;
import com.mobisec.in.userservice.dto.UpdateProfileRequest;
import com.mobisec.in.userservice.dto.UserProfileResponse;
import com.mobisec.in.userservice.dto.UserVerifiedEvent;

import java.util.List;
import java.util.UUID;

public interface UserService {
    // Called by RabbitMQ consumer — creates profile on email verification
    void createUserProfile(UserVerifiedEvent event);

    // GET /me — own profile
    UserProfileResponse getMyProfile(UUID userId);

    // GET /{userId} — own profile or admin
    UserProfileResponse getUserById(UUID requestingUserId, String requestingUserRole, UUID targetUserId);

    // PUT /me — update own profile
    UserProfileResponse updateMyProfile(UUID userId, UpdateProfileRequest request);

    // GET / — admin only
    List<UserProfileResponse> getAllUsers();

    // Internal — called only by other services via SERVICE token
    InternalUserVerifyResponse verifyUserForService(UUID targetUserId, String expectedRole);
}
