package com.mobisec.in.userservice.service.UserService;

import com.mobisec.in.userservice.dto.UserVerifiedEvent;
import com.mobisec.in.userservice.entity.UserProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserService {
    void createUserProfile(UserVerifiedEvent event);
    Optional<UserProfile> getUserById(UUID id);
    List<UserProfile> getAllUsers();
}
