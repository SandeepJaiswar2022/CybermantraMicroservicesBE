package com.mobisec.in.userservice.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class UserProfileResponse {
    private UUID userId;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private boolean isActive;
    private LocalDateTime createdAt;
}