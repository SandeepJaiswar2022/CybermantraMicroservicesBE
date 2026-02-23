package com.mobisec.in.courseservice.service.section.lecture;

import com.mobisec.in.courseservice.dto.lecture.CreateLectureRequest;
import com.mobisec.in.courseservice.dto.lecture.LectureResponse;
import com.mobisec.in.courseservice.dto.lecture.ReorderLecturesRequest;
import com.mobisec.in.courseservice.dto.lecture.UpdateLectureRequest;
import com.mobisec.in.courseservice.dto.section.SectionWithLecturesResponse;
import com.mobisec.in.courseservice.entity.Course;
import com.mobisec.in.courseservice.entity.CourseSection;
import com.mobisec.in.courseservice.entity.Lecture;
import com.mobisec.in.courseservice.enums.CourseStatus;
import com.mobisec.in.courseservice.enums.LectureContentType;
import com.mobisec.in.courseservice.exception.ForbiddenException;
import com.mobisec.in.courseservice.exception.InvalidInputException;
import com.mobisec.in.courseservice.mapper.LectureMapper;
import com.mobisec.in.courseservice.repository.CourseRepository;
import com.mobisec.in.courseservice.repository.LectureRepository;
import com.mobisec.in.courseservice.repository.SectionRepository;
import com.mobisec.in.courseservice.service.lecture.LectureServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LectureServiceTest {

    @Mock
    LectureRepository lectureRepository;

    @Mock
    SectionRepository sectionRepository;

    @Mock
    CourseRepository courseRepository;

    @Mock
    LectureMapper lectureMapper;

    @InjectMocks
    LectureServiceImpl lectureService;

    UUID courseId = UUID.randomUUID();
    UUID sectionId = UUID.randomUUID();
    UUID lectureId = UUID.randomUUID();
    UUID instructorId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();

    Course course;
    CourseSection section;
    Lecture lecture;

    @BeforeEach
    void setup() {
        course = Course.builder()
                .id(courseId)
                .instructorId(instructorId)
                .status(CourseStatus.PUBLISHED)
                .build();

        section = CourseSection.builder()
                .id(sectionId)
                .course(course)
                .build();

        lecture = Lecture.builder()
                .id(lectureId)
                .section(section)
                .orderIndex(0)
                .isPreview(false)
                .isCompletedByInstructor(true)
                .durationSeconds(100)
                .build();
    }

    // ================= CREATE =================

    @Test
    void createLecture_success_video() {
        CreateLectureRequest request = CreateLectureRequest.builder()
                .title("Intro")
                .contentType(LectureContentType.VIDEO)
                .videoUrl("https://video.com/1")
                .durationSeconds(120)
                .build();

        when(sectionRepository.findByIdWithCourse(sectionId))
                .thenReturn(Optional.of(section));

        when(lectureRepository.findMaxOrderIndexBySectionId(sectionId))
                .thenReturn(0);

        when(lectureRepository.save(any()))
                .thenReturn(lecture);

        when(lectureMapper.toResponse(any()))
                .thenReturn(new LectureResponse());

        LectureResponse response = lectureService.createLecture(
                courseId, sectionId, request, "INSTRUCTOR", instructorId
        );

        assertNotNull(response);
        verify(lectureRepository).save(any(Lecture.class));
    }

    @Test
    void createLecture_forbidden_whenNotOwner() {
        CreateLectureRequest request = CreateLectureRequest.builder()
                .contentType(LectureContentType.VIDEO)
                .videoUrl("https://video.com")
                .durationSeconds(10)
                .build();

        when(sectionRepository.findByIdWithCourse(sectionId))
                .thenReturn(Optional.of(section));

        assertThrows(ForbiddenException.class, () ->
                lectureService.createLecture(
                        courseId, sectionId, request, "INSTRUCTOR", studentId
                )
        );
    }

    @Test
    void createLecture_invalid_videoUrl() {
        CreateLectureRequest request = CreateLectureRequest.builder()
                .contentType(LectureContentType.VIDEO)
                .videoUrl("invalid")
                .durationSeconds(10)
                .build();

        when(sectionRepository.findByIdWithCourse(sectionId))
                .thenReturn(Optional.of(section));

        assertThrows(InvalidInputException.class, () ->
                lectureService.createLecture(
                        courseId, sectionId, request, "INSTRUCTOR", instructorId
                )
        );
    }

    // ================= GET ALL =================

    @Test
    void getAllLectures_enrolledUser_getsAll() {
        when(sectionRepository.findByIdWithCourse(sectionId))
                .thenReturn(Optional.of(section));

        when(lectureRepository.findBySectionIdOrderByOrderIndexAsc(sectionId))
                .thenReturn(List.of(lecture));

        when(lectureMapper.toResponseWithLectures(any()))
                .thenReturn(new SectionWithLecturesResponse());

        SectionWithLecturesResponse response =
                lectureService.getAllLecturesBySection(
                        courseId, sectionId, studentId, "STUDENT", true
                );

        assertNotNull(response);
    }

    @Test
    void getAllLectures_publicUser_onlyPreview() {
        lecture.setIsPreview(true);

        when(sectionRepository.findByIdWithCourse(sectionId))
                .thenReturn(Optional.of(section));

        when(lectureRepository.findBySectionIdOrderByOrderIndexAsc(sectionId))
                .thenReturn(List.of(lecture));

        when(lectureMapper.toResponseWithLectures(any()))
                .thenReturn(new SectionWithLecturesResponse());

        SectionWithLecturesResponse response =
                lectureService.getAllLecturesBySection(
                        courseId, sectionId, null, "PUBLIC", false
                );

        assertNotNull(response);
    }

    // ================= GET BY ID =================

    @Test
    void getLectureById_success() {
        when(lectureRepository.findByIdAndSectionId(lectureId, sectionId))
                .thenReturn(Optional.of(lecture));

        when(lectureMapper.toResponse(any()))
                .thenReturn(new LectureResponse());

        LectureResponse response =
                lectureService.getLectureById(
                        courseId, sectionId, lectureId, studentId, "STUDENT", true
                );

        assertNotNull(response);
    }

    @Test
    void getLectureById_forbidden() {
        lecture.setIsCompletedByInstructor(false);

        when(lectureRepository.findByIdAndSectionId(lectureId, sectionId))
                .thenReturn(Optional.of(lecture));

        assertThrows(ForbiddenException.class, () ->
                lectureService.getLectureById(
                        courseId, sectionId, lectureId, studentId, "STUDENT", true
                )
        );
    }

    // ================= UPDATE =================

    @Test
    void updateLecture_success() {
        UpdateLectureRequest request = UpdateLectureRequest.builder()
                .title("Updated")
                .durationSeconds(200)
                .build();

        when(lectureRepository.findByIdAndSectionId(lectureId, sectionId))
                .thenReturn(Optional.of(lecture));

        when(lectureRepository.save(any()))
                .thenReturn(lecture);

        when(lectureMapper.toResponse(any()))
                .thenReturn(new LectureResponse());

        LectureResponse response =
                lectureService.updateLecture(
                        courseId, sectionId, lectureId, request, "INSTRUCTOR", instructorId
                );

        assertNotNull(response);
        verify(sectionRepository).save(any());
    }

    // ================= DELETE =================

    @Test
    void deleteLecture_success() {
        when(lectureRepository.findByIdAndSectionId(lectureId, sectionId))
                .thenReturn(Optional.of(lecture));

        when(lectureRepository.findBySectionIdOrderByOrderIndexAsc(sectionId))
                .thenReturn(List.of());

        lectureService.deleteLecture(
                courseId, sectionId, lectureId, "INSTRUCTOR", instructorId
        );

        verify(lectureRepository).delete(lecture);
    }

    // ================= REORDER =================

    @Test
    void reorderLectures_success() {
        ReorderLecturesRequest request =
                new ReorderLecturesRequest(List.of(
                        new ReorderLecturesRequest.LectureOrderItem(lectureId, 0)
                ));

        when(sectionRepository.findByIdWithCourse(sectionId))
                .thenReturn(Optional.of(section));

        when(lectureRepository.findBySectionIdOrderByOrderIndexAsc(sectionId))
                .thenReturn(List.of(lecture));

        when(lectureMapper.toResponse(any()))
                .thenReturn(new LectureResponse());

        List<LectureResponse> responses =
                lectureService.reorderLectures(
                        courseId, sectionId, request, "INSTRUCTOR", instructorId
                );

        assertEquals(1, responses.size());
    }

    @Test
    void reorderLectures_duplicateIds() {
        ReorderLecturesRequest request =
                new ReorderLecturesRequest(List.of(
                        new ReorderLecturesRequest.LectureOrderItem(lectureId, 0),
                        new ReorderLecturesRequest.LectureOrderItem(lectureId, 1)
                ));

        when(sectionRepository.findByIdWithCourse(sectionId))
                .thenReturn(Optional.of(section));

        assertThrows(InvalidInputException.class, () ->
                lectureService.reorderLectures(
                        courseId, sectionId, request, "INSTRUCTOR", instructorId
                )
        );
    }
}

