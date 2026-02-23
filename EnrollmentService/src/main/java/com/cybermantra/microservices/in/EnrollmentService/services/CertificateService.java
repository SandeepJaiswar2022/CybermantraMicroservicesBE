package com.cybermantra.microservices.in.EnrollmentService.services;
import com.cybermantra.microservices.in.EnrollmentService.dto.response.CertificateResponse;
import com.cybermantra.microservices.in.EnrollmentService.exceptions.AccessDeniedException;
import com.cybermantra.microservices.in.EnrollmentService.exceptions.CourseNotCompletedException;
import com.cybermantra.microservices.in.EnrollmentService.models.Certificate;
import com.cybermantra.microservices.in.EnrollmentService.models.Enrollment;
import com.cybermantra.microservices.in.EnrollmentService.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final EnrollmentService enrollmentService;

    @Transactional
    public CertificateResponse getOrGenerateCertificate(Long enrollmentId, Long userId) {
        Enrollment enrollment = enrollmentService.findEnrollmentById(enrollmentId);

        if (!enrollment.getUserId().equals(userId)) {
            throw new AccessDeniedException();
        }

        if (!Boolean.TRUE.equals(enrollment.getIsCompleted())) {
            throw new CourseNotCompletedException(enrollmentId);
        }

        // Return existing certificate or generate new one
        return certificateRepository.findByEnrollmentId(enrollmentId)
                .map(this::mapToResponse)
                .orElseGet(() -> generateCertificate(enrollment));
    }

    private CertificateResponse generateCertificate(Enrollment enrollment) {
        String verificationCode = generateVerificationCode();

        Certificate certificate = Certificate.builder()
                .enrollment(enrollment)
                .verificationCode(verificationCode)
                // pdfUrl would be set after PDF generation (e.g., S3 upload)
                .pdfUrl(generatePdfUrl(enrollment, verificationCode))
                .build();

        certificate = certificateRepository.save(certificate);
        log.info("Certificate generated for enrollment {}: {}",
                enrollment.getId(), certificate.getCertificateId());

        return mapToResponse(certificate);
    }

    private String generateVerificationCode() {
        return "CERT-" + UUID.randomUUID().toString().toUpperCase().substring(0, 12);
    }

    private String generatePdfUrl(Enrollment enrollment, String verificationCode) {
        // In production: generate actual PDF and upload to S3, return URL
        // For now: return a placeholder / verification endpoint URL
        return "/api/v1/certificates/verify/" + verificationCode;
    }

    private CertificateResponse mapToResponse(Certificate cert) {
        return CertificateResponse.builder()
                .id(cert.getId())
                .certificateId(cert.getCertificateId())
                .enrollmentId(cert.getEnrollment().getId())
                .userId(cert.getEnrollment().getUserId())
                .courseId(cert.getEnrollment().getCourseId())
                .issuedDate(cert.getIssuedDate())
                .pdfUrl(cert.getPdfUrl())
                .verificationCode(cert.getVerificationCode())
                .build();
    }
}