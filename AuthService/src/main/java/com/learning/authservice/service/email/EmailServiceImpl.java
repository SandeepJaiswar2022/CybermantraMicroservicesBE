package com.learning.authservice.service.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.verification-url}")
    private String verificationUrl;

    @Async
    @Override
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(fromEmail, "Auth Service");
            helper.setTo(toEmail);
            helper.setSubject("Verify Your Email Address");

            String verificationLink = verificationUrl + "?token=" + token;
            String html = buildVerificationEmail(fullName, verificationLink);

            helper.setText(html, true);

            mailSender.send(message);
            log.info("Verification email sent to {}", toEmail);

        } catch (Exception ex) {
            log.error("Failed to send verification email to {}: {}", toEmail, ex.getMessage());
        }
    }

    private String buildVerificationEmail(String fullName, String verificationLink) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif;">
                    <h2>Hello, %s!</h2>
                    <p>Thank you for registering. Click below to verify your email:</p>
                    
                    <a href="%s" style="
                        padding: 12px 22px;
                        background-color: #007bff;
                        color: white;
                        text-decoration: none;
                        border-radius: 6px;
                    ">Verify Email</a>

                    <p>If the button doesn’t work, copy this link:</p>
                    <p style="word-break: break-all; color: #007bff;">%s</p>

                    <p>This link expires in 24 hours.</p>
                </body>
                </html>
               """.formatted(fullName, verificationLink, verificationLink);
    }
}
