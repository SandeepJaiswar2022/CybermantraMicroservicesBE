package com.mobisec.in.userservice.service.UserService;

// ============================================================================
// service/UserService.java
// ============================================================================

import com.mobisec.in.userservice.dto.UserVerifiedEvent;
import com.mobisec.in.userservice.entity.UserProfile;
import com.mobisec.in.userservice.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserProfileRepository userProfileRepository;

    /**
     * Create user profile with idempotency guarantee
     */
    @Transactional
    public void createUserProfile(UserVerifiedEvent event) {
        // Idempotency check - skip if already exists
        if (userProfileRepository.existsByUserId(event.getUserId())) {
            log.info("User profile already exists for userId: {}. Skipping creation.", event.getUserId());
            return;
        }

        try {
            UserProfile profile = new UserProfile();
            profile.setUserId(event.getUserId());
            profile.setFullName(event.getFullName());
            profile.setEmail(event.getEmail());

            userProfileRepository.save(profile);
            log.info("User profile created successfully for userId: {}", event.getUserId());

        } catch (DataIntegrityViolationException e) {
            // Handle race condition - another thread might have created the profile
            log.warn("Constraint violation while creating profile for userId: {}. Profile may already exist.",
                    event.getUserId());
            // This is acceptable - idempotency is maintained
        }
    }

    public Optional<UserProfile> getUserById(UUID id) {
        return userProfileRepository.findById(id);
    }

    public List<UserProfile> getAllUsers() {
        return userProfileRepository.findAll();
    }
}