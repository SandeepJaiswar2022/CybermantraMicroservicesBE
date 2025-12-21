package com.mobisec.in.userservice.dto;

// ============================================================================
// dto/UserVerifiedEvent.java
// ============================================================================
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVerifiedEvent implements Serializable {
    private UUID userId;
    private String fullName;
    private String email;
    private String role;
}