package com.mobisec.in.courseservice.dto.internal;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ServiceTokenResponse {
    private String token;
    private long expiresInSeconds;
    private String serviceName;
}