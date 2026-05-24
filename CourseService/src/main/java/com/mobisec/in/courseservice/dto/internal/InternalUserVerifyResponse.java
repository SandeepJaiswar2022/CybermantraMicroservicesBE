package com.mobisec.in.courseservice.dto.internal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class InternalUserVerifyResponse {
    private UUID userId;
    private String fullName;
    private String email;
    private boolean isValidRole;   // server-side asserted — not raw role string
    private boolean isActive;
}