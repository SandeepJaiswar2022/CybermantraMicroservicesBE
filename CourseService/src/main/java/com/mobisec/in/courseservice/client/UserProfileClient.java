package com.mobisec.in.courseservice.client;

import com.mobisec.in.courseservice.dto.common.ApiResponse;
import com.mobisec.in.courseservice.dto.internal.InternalUserVerifyResponse;
import com.mobisec.in.courseservice.exception.ForbiddenException;
import com.mobisec.in.courseservice.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserProfileClient {

    private final RestTemplate restTemplate;
    private final ServiceTokenManager serviceTokenManager;

    @Value("${internal.user-profile-service-url}")
    private String userProfileServiceUrl;

    /**
     * Verifies instructor exists, has INSTRUCTOR role, and is active.
     * Called only from ADMIN course-creation flow.
     * Returns full InternalUserVerifyResponse so caller can
     * extract instructorName for denormalization into the course.
     */
    public InternalUserVerifyResponse verifyInstructor(UUID instructorId) {

        log.info("Verifying instructor: {}", instructorId);

        try {

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(serviceTokenManager.getServiceToken());

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<ApiResponse<InternalUserVerifyResponse>> response =
                    restTemplate.exchange(
                            userProfileServiceUrl +
                                    "/api/v1/internal/users/{userId}/verify?expectedRole=INSTRUCTOR",
                            HttpMethod.GET,
                            requestEntity,
                            new ParameterizedTypeReference<>() {
                            },
                            instructorId
                    );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ResourceNotFoundException("Instructor not found: " + instructorId);
            }

            InternalUserVerifyResponse data = response.getBody().getData();

            if (data == null) {
                throw new ResourceNotFoundException("Instructor not found: " + instructorId);
            }

            boolean isValidRole = data.isValidRole();
            boolean isActive = data.isActive();

            if (!isValidRole) {
                throw new ForbiddenException(
                        "User does not have INSTRUCTOR role");
            }

            if (!isActive) {
                throw new ForbiddenException(
                        "Account is inactive!");
            }

            log.info("Instructor verified successfully: {}", instructorId);

            return data;

        } catch (HttpClientErrorException.NotFound e) {

            throw new ResourceNotFoundException("Instructor not found");

        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {

            log.error(
                    "Service authentication failed calling user-profile-service. Status: {}",
                    e.getStatusCode()
            );

            throw new RuntimeException("Internal service authentication failed");

        } catch (ResourceNotFoundException | ForbiddenException e) {

            throw e;

        } catch (Exception e) {

            log.error(
                    "Unexpected error calling user-profile-service for instructorId: {}",
                    instructorId,
                    e
            );

            throw new RuntimeException("Failed to verify instructor. Please try again.");
        }
    }
}