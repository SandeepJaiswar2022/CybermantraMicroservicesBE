package com.learning.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class ServiceTokenRequest {

    @NotBlank(message = "Service name must not be blank")
    private String serviceName;

    @NotBlank(message = "Service secret must not be blank")
    private String serviceSecret;
}