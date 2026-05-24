package com.mobisec.in.contentservice.security;

public class JwtAuthenticationException extends RuntimeException {
    public JwtAuthenticationException(String message) { super(message); }
}
