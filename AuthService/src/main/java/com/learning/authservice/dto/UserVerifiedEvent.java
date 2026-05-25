package com.learning.authservice.dto;

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
    private String firstName;
    private String lastName;
    private String email;
    private String role;
}