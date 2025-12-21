package com.learning.authservice.service.email;

public interface EmailService {
    void sendVerificationEmail(String toEmail, String fullName, String token);
}
