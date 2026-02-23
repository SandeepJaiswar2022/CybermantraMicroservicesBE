package com.cybermantra.microservices.in.EnrollmentService.repository;

import com.cybermantra.microservices.in.EnrollmentService.models.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findByEnrollmentId(Long enrollmentId);

    Optional<Certificate> findByCertificateId(UUID certificateId);

    Optional<Certificate> findByVerificationCode(String verificationCode);
}