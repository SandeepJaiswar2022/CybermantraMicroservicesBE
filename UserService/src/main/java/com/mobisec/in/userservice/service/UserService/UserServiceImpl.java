package com.mobisec.in.userservice.service.UserService;

import com.mobisec.in.userservice.dto.InternalUserVerifyResponse;
import com.mobisec.in.userservice.dto.UpdateProfileRequest;
import com.mobisec.in.userservice.dto.UserProfileResponse;

// ============================================================================
// service/UserService.java
// ============================================================================

import com.mobisec.in.userservice.dto.UserVerifiedEvent;
import com.mobisec.in.userservice.entity.UserProfile;
import com.mobisec.in.userservice.exception.ForbiddenException;
import com.mobisec.in.userservice.exception.ResourceNotFoundException;
import com.mobisec.in.userservice.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserProfileRepository userProfileRepository;

    /**
     * Creates user profile on email verification event.
     * Idempotent — safe to call multiple times for same userId.
     */
    @Override
    @Transactional
    public void createUserProfile(UserVerifiedEvent event) {
        if (userProfileRepository.existsByUserId(event.getUserId())) {
            log.info("User profile already exists for userId: {}. Skipping.", event.getUserId());
            return;
        }

        try {
            UserProfile profile = UserProfile.builder()
                    .userId(event.getUserId())
                    .fullName(event.getFullName())
                    .email(event.getEmail())
                    .role(event.getRole())
                    .isActive(true)
                    .build();

            userProfileRepository.save(profile);
            log.info("User profile created for userId: {}", event.getUserId());

        } catch (DataIntegrityViolationException e) {
            // Race condition — another thread created the profile between our check and
            // save
            // Idempotency maintained — this is safe to swallow
            log.warn("Constraint violation for userId: {}. Profile likely already exists.", event.getUserId());
        }
    }

    /**
     * Returns own profile — any authenticated user.
     */
    @Override
    public UserProfileResponse getMyProfile(UUID userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        return mapToResponse(profile);
    }

    /**
     * Returns profile by userId.
     * INSTRUCTOR/STUDENT can only fetch their own profile.
     * ADMIN can fetch any profile.
     */
    @Override
    public UserProfileResponse getUserById(UUID requestingUserId, String requestingUserRole, UUID targetUserId) {
        boolean isAdmin = "ADMIN".equals(requestingUserRole);
        boolean isOwnProfile = requestingUserId.equals(targetUserId);

        if (!isAdmin && !isOwnProfile) {
            throw new ForbiddenException("You are not allowed to view this profile");
        }

        UserProfile profile = userProfileRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return mapToResponse(profile);
    }

    /**
     * Updates own profile — any authenticated user.
     * Only fullName is updatable — email and role are immutable from
     * profile-service.
     */
    @Override
    @Transactional
    public UserProfileResponse updateMyProfile(UUID userId, UpdateProfileRequest request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));

        profile.setFullName(request.getFullName());
        profile.setUpdatedAt(LocalDateTime.now());

        UserProfile updated = userProfileRepository.save(profile);
        log.info("Profile updated for userId: {}", userId);

        return mapToResponse(updated);
    }

    /**
     * Returns all profiles — ADMIN only.
     * Authorization enforced at controller level via @PreAuthorize.
     */
    @Override
    public List<UserProfileResponse> getAllUsers() {
        return userProfileRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Internal verification — called ONLY by other services via SERVICE token.
     * Performs role assertion server-side — returns boolean, not raw role.
     * Never exposes sensitive fields.
     */
    @Override
    public InternalUserVerifyResponse verifyUserForService(UUID targetUserId, String expectedRole) {
        UserProfile profile = userProfileRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isValidRole = expectedRole != null
                ? expectedRole.equalsIgnoreCase(profile.getRole())
                : true; // if no role expectation, just verify existence and active status

        return InternalUserVerifyResponse.builder()
                .userId(profile.getUserId())
                .fullName(profile.getFullName())
                .email(profile.getEmail())
                .isValidRole(isValidRole)
                .isActive(profile.isActive())
                .build();
    }

    private UserProfileResponse mapToResponse(UserProfile profile) {
        return UserProfileResponse.builder()
                .userId(profile.getUserId())
                .fullName(profile.getFullName())
                .email(profile.getEmail())
                .role(profile.getRole())
                .isActive(profile.isActive())
                .createdAt(profile.getCreatedAt())
                .build();
    }
}