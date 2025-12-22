package com.learning.authservice.service.jwt;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;
import java.util.function.Function;

public interface JwtService {
    String generateAccessToken(UUID userID,String role);
}
