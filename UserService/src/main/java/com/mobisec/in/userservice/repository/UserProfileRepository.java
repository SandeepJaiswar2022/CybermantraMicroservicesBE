package com.mobisec.in.userservice.repository;

// ============================================================================
// repository/UserProfileRepository.java
// ============================================================================

import com.mobisec.in.userservice.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    boolean existsByUserId(UUID userId);
}
