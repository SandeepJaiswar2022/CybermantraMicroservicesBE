package com.learning.authservice.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
public class UserDto {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    @JsonProperty("isEmailVerified")
    private boolean isEmailVerified;
}