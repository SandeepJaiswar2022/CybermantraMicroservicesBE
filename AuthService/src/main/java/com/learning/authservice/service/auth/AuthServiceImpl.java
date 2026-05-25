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

    @Override
    public RegisterResponse register(RegisterRequest req) {
        // Check if user already exists
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new AlreadyExistException("Email already exist!");
        }
        // Create new user
        var role = Role.valueOf("STUDENT");
        User user = User.builder()
                .email(req.getEmail())
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(role)
                .isEmailVerified(false)
                .build();

        // Generate verification token
        String verificationToken = CryptoUtils.generateRandomToken(32);
        user.setEmailVerificationToken(verificationToken);
        user.setEmailVerificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS));

        // Save User to database
        User savedUser = userRepository.save(user);

        String fullName = user.getFirstName() + " " + user.getLastName();
        // Send verification email (async - won't block response)
        emailService.sendVerificationEmail(
                user.getEmail(),
                fullName,
                verificationToken);

        log.info("User registered successfully: {}", savedUser.getEmail());

        return RegisterResponse.builder()
                .email(user.getEmail())
                .build();
    }

    private Map<String, Object> getAuthResponseAndRefreshToken(User user, String refreshToken) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole().name());

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken(accessToken)
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .isEmailVerified(user.isEmailVerified())
                .build();
        Map<String, Object> result = new HashMap<>();
        result.put("authResponse", authResponse);
        result.put("refreshToken", refreshToken);

        return result;
    }

    public Map<String, Object> login(LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
            User user = (User) auth.getPrincipal();

            if (!user.isEmailVerified()) {
                throw new AuthException("Please verify email before login!");
            }

            String refreshToken = refreshTokenService.createTokenFamily(
                    user.getId(), user.getRole().name());
            return getAuthResponseAndRefreshToken(user, refreshToken);

        } catch (BadCredentialsException | UsernameNotFoundException e) {
            // Generic message - don't reveal if email exists or password is wrong
            throw new AuthException("Invalid email or password!");
        }
    }

    @Transactional
    public String verifyEmail(String token) {
        // Find user by token
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new AuthException("Invalid verification token"));

        // Check if token is expired
        if (user.getEmailVerificationTokenExpiry().isBefore(Instant.now())) {
            throw new AuthException("Verification token has expired");
        }

        // Check if already verified
        if (user.isEmailVerified()) {
            return "Email already verified";
        }

        // Mark as verified
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        userRepository.save(user);

        // Publish event to RabbitMQ
        UserVerifiedEvent event = new UserVerifiedEvent(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole().name());
        eventPublisher.publishUserVerifiedEvent(event);

        log.info("Email verified successfully for user: {}", user.getEmail());

        return "Email verified successfully";
    }

}