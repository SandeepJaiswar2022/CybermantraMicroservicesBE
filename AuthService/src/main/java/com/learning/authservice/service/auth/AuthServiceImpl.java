package com.learning.authservice.service.auth;

import com.learning.authservice.Enum.Role;
import com.learning.authservice.dto.*;
import com.learning.authservice.entity.User;
import com.learning.authservice.exception.AlreadyExistException;
import com.learning.authservice.exception.AuthException;
import com.learning.authservice.repository.UserRepository;
import com.learning.authservice.service.email.EmailService;
import com.learning.authservice.service.eventPublisher.EventPublisher;
import com.learning.authservice.service.jwt.JwtService;
import com.learning.authservice.service.refreshToken.RefreshTokenService;
import com.learning.authservice.utils.CryptoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * High-level auth orchestration: register, login, logout, refresh (delegates to
 * RefreshTokenService).
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final EventPublisher eventPublisher;

    @Value("${jwt.access-token-expiry-seconds}")
    private long accessTokenExpirySeconds;

    @Override
    public RegisterResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new AlreadyExistException("Email already exists!");
        }

        String verificationToken = CryptoUtils.generateRandomToken(32);

        User user = User.builder()
                .email(req.getEmail())
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(Role.STUDENT)
                .isEmailVerified(false)
                .emailVerificationToken(verificationToken)
                .emailVerificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        userRepository.save(user);
        emailService.sendVerificationEmail(user.getEmail(),
                user.getFirstName() + " " + user.getLastName(), verificationToken);

        log.info("User registered: {}", user.getEmail());
        return RegisterResponse.builder().email(user.getEmail()).build();
    }

    /**
     * Authenticates user, creates token family in Redis, returns consistent
     * AuthResponse.
     * The raw refreshToken is returned separately for the controller to set as
     * HttpOnly cookie.
     */
    @Override
    public LoginResult login(LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
            User user = (User) auth.getPrincipal();

            if (!user.isEmailVerified()) {
                throw new AuthException("Please verify your email before logging in.");
            }

            // Store full user snapshot in Redis so refresh never hits the DB
            String rawRefreshToken = refreshTokenService.createTokenFamily(
                    user.getId(), user.getRole().name(),
                    user.getEmail(), user.getFirstName(), user.getLastName(),
                    user.isEmailVerified());

            String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());

            AuthResponse authResponse = AuthResponse.builder()
                    .accessToken(accessToken)
                    .expiresIn(accessTokenExpirySeconds)
                    .user(UserDto.builder()
                            .id(user.getId())
                            .email(user.getEmail())
                            .firstName(user.getFirstName())
                            .lastName(user.getLastName())
                            .role(user.getRole().name())
                            .isEmailVerified(user.isEmailVerified())
                            .build())
                    .build();

            return new LoginResult(authResponse, rawRefreshToken);

        } catch (BadCredentialsException | UsernameNotFoundException e) {
            throw new AuthException("Invalid email or password.");
        }
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken != null) {
            String familyId = refreshToken.split(":", 2)[0];
            refreshTokenService.revokeFamily(familyId);
        }
    }

    /**
     * Validates and rotates refresh token via Redis Lua CAS.
     * Generates new access token from Redis user snapshot — zero DB call.
     * Returns new raw refreshToken for the controller to rotate the cookie.
     */
    @Override
    public RefreshResult refresh(String incomingRefreshToken) {
        var rotated = refreshTokenService.rotateIfValid(incomingRefreshToken);

        String accessToken = jwtService.generateAccessToken(
                rotated.user().getId(), rotated.user().getRole());

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .expiresIn(accessTokenExpirySeconds)
                .user(rotated.user())
                .build();

        return new RefreshResult(authResponse, rotated.newRefreshToken());
    }

    @Override
    @Transactional
    public String verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new AuthException("Invalid verification token."));

        if (user.getEmailVerificationTokenExpiry().isBefore(Instant.now())) {
            throw new AuthException("Verification token has expired.");
        }
        if (user.isEmailVerified()) {
            return "Email already verified.";
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        userRepository.save(user);

        eventPublisher.publishUserVerifiedEvent(new UserVerifiedEvent(
                user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getRole().name()));

        log.info("Email verified for: {}", user.getEmail());
        return "Email verified successfully.";
    }
}