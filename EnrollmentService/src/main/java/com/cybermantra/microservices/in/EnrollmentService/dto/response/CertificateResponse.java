package com.cybermantra.microservices.in.EnrollmentService.dto.response;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CertificateResponse {
    private Long id;
    private UUID certificateId;
    private Long enrollmentId;
    private UUID userId;
    private Long courseId;
    private LocalDateTime issuedDate;
    private String pdfUrl;
    private String verificationCode;
}