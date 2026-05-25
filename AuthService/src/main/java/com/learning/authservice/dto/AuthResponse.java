package com.learning.authservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AuthResponse {
    private String accessToken;
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private Boolean isEmailVerified;
}
