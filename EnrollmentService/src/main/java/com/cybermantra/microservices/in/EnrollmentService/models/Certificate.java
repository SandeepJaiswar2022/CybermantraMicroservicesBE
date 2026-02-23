package com.cybermantra.microservices.in.EnrollmentService.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "certificates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false, unique = true)
    private Enrollment enrollment;

    @Column(name = "certificate_id", unique = true)
    @Builder.Default
    private UUID certificateId = UUID.randomUUID();

    @CreationTimestamp
    @Column(name = "issued_date", updatable = false)
    private LocalDateTime issuedDate;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "verification_code", unique = true, length = 50)
    private String verificationCode;
}