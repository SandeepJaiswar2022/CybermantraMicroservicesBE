package com.cybermantra.microservices.in.EnrollmentService.services;
import com.cybermantra.microservices.in.EnrollmentService.dto.request.NoteRequest;
import com.cybermantra.microservices.in.EnrollmentService.dto.response.NoteResponse;
import com.cybermantra.microservices.in.EnrollmentService.exceptions.AccessDeniedException;
import com.cybermantra.microservices.in.EnrollmentService.models.CourseNote;
import com.cybermantra.microservices.in.EnrollmentService.models.Enrollment;
import com.cybermantra.microservices.in.EnrollmentService.repository.CourseNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final CourseNoteRepository courseNoteRepository;
    private final EnrollmentService enrollmentService;

    @Transactional
    public NoteResponse addNote(Long enrollmentId, UUID userId, NoteRequest request) {
        Enrollment enrollment = enrollmentService.findEnrollmentById(enrollmentId);
        verifyOwnership(enrollment, userId);

        CourseNote note = CourseNote.builder()
                .enrollment(enrollment)
                .lectureId(request.getLectureId())
                .noteText(request.getNoteText())
                .timestampSeconds(request.getTimestampSeconds())
                .build();

        return mapToResponse(courseNoteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> getNotes(Long enrollmentId, UUID userId) {
        Enrollment enrollment = enrollmentService.findEnrollmentById(enrollmentId);
        verifyOwnership(enrollment, userId);

        return courseNoteRepository.findAllByEnrollmentId(enrollmentId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private void verifyOwnership(Enrollment enrollment, UUID userId) {
        if (!enrollment.getUserId().equals(userId)) {
            throw new AccessDeniedException();
        }
    }

    private NoteResponse mapToResponse(CourseNote note) {
        return NoteResponse.builder()
                .id(note.getId())
                .lectureId(note.getLectureId())
                .noteText(note.getNoteText())
                .timestampSeconds(note.getTimestampSeconds())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}