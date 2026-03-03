package com.mobisec.in.userservice.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.UUID;

@Getter
@Builder
public class InternalUserVerifyResponse {
    private UUID userId;
    private String fullName;
    private String email;
    private boolean isValidRole; // server-side role assertion — not raw role string
    private boolean isActive;
}