package com.learning.authservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.learning.authservice.dto.ApiResponse;
import com.learning.authservice.dto.ServiceTokenRequest;
import com.learning.authservice.dto.ServiceTokenResponse;
import com.learning.authservice.service.ServiceToken.IssueServiceTokenService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("${api.base-url}/internal/auth")
@RequiredArgsConstructor
@Slf4j
public class ServiceAuthController {

    private final IssueServiceTokenService issueServiceTokenService;

    /**
     * Issues a SERVICE-scoped JWT to authenticated microservices.
     *
     * Called by: course-service, user-profile-service, etc. at startup or token
     * refresh.
     * NOT called by users. Gateway must block /internal/** from external traffic.
     *
     * Authentication: serviceName + serviceSecret in request body (bootstrap call —
     * no JWT yet)
     */
    @PostMapping("/service-token")
    public ResponseEntity<ApiResponse<ServiceTokenResponse>> issueServiceToken(
            @Valid @RequestBody ServiceTokenRequest request) {

        log.info("Service token requested by: {}", request.getServiceName());

        ServiceTokenResponse response = issueServiceTokenService.issueServiceToken(request);

        return ResponseEntity.ok(ApiResponse.success("Service token issued", response));
    }
}