package com.learning.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ServiceTokenResponse {
    private String token;
    private long expiresInSeconds;
    private String serviceName;
}