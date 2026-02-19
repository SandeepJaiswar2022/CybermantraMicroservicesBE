package com.mobisec.in.courseservice.service.section;



import com.mobisec.in.courseservice.dto.section.*;
import com.mobisec.in.courseservice.entity.Course;
import com.mobisec.in.courseservice.entity.CourseSection;
import com.mobisec.in.courseservice.exception.ForbiddenException;
import com.mobisec.in.courseservice.exception.ResourceNotFoundException;
import com.mobisec.in.courseservice.mapper.SectionMapper;
import com.mobisec.in.courseservice.repository.CourseRepository;
import com.mobisec.in.courseservice.repository.SectionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;



@ExtendWith(MockitoExtension.class)
@DisplayName("Section Service Tests")
class SectionServiceTest {

    @Mock
    private SectionRepository sectionRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private SectionMapper sectionMapper;



    @InjectMocks
    private SectionServiceImpl sectionService;

    private UUID courseId;
    private UUID instructorId;
    private UUID adminId;
    private UUID sectionId;
    private Course course;
    private CourseSection section;
    private final String instructorRole = "INSTRUCTOR";
    private final String adminRole = "ADMIN";

    @BeforeEach
    void setUp() {
        courseId = UUID.randomUUID();
        instructorId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        sectionId = UUID.randomUUID();

        course = Course.builder()
                .id(courseId)
                .instructorId(instructorId)
                .title("Test Course")
                .build();

        section = CourseSection.builder()
                .id(sectionId)
                .course(course)
                .title("Test Section")
                .orderIndex(0)
                .build();
    }

    // ==================== CREATE SECTION TESTS ====================

    @Test
    @DisplayName("Should create section successfully as instructor (course owner)")
    void testCreateSection_AsInstructor_Success() {
        // Arrange
        CreateSectionRequest request = CreateSectionRequest.builder()
                .title("Introduction")
                .description("Course introduction")
                .objective("Learn basics")
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findMaxOrderIndexByCourseId(courseId)).thenReturn(-1);
        when(sectionRepository.save(any(CourseSection.class))).thenReturn(section);
        when(sectionRepository.countByCourseId(courseId)).thenReturn(1L);
        when(sectionMapper.toResponse(any(CourseSection.class)))
                .thenReturn(SectionResponse.builder().id(sectionId).build());

        // Act
        SectionResponse response = sectionService.createSection(courseId, request, instructorRole, instructorId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(sectionId);
        verify(courseRepository).findById(courseId);
        verify(sectionRepository).save(any(CourseSection.class));
        verify(courseRepository, times(2)).save(course);
    }

    @Test
    @DisplayName("Should create section successfully as admin")
    void testCreateSection_AsAdmin_Success() {
        // Arrange
        CreateSectionRequest request = CreateSectionRequest.builder()
                .title("Introduction")
                .description("Course introduction")
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findMaxOrderIndexByCourseId(courseId)).thenReturn(-1);
        when(sectionRepository.save(any(CourseSection.class))).thenReturn(section);
        when(sectionRepository.countByCourseId(courseId)).thenReturn(1L);
        when(sectionMapper.toResponse(any(CourseSection.class)))
                .thenReturn(SectionResponse.builder().id(sectionId).build());

        // Act
        SectionResponse response = sectionService.createSection(courseId, request, adminRole, adminId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(sectionId);
        verify(sectionRepository).save(any(CourseSection.class));
    }

    @Test
    @DisplayName("Should throw exception when course not found")
    void testCreateSection_CourseNotFound() {
        // Arrange
        CreateSectionRequest request = CreateSectionRequest.builder()
                .title("Test")
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> sectionService.createSection(courseId, request, instructorRole, instructorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Course not found");
    }

    @Test
    @DisplayName("Should throw exception when instructor is not course owner")
    void testCreateSection_NotOwner() {
        // Arrange
        UUID differentInstructorId = UUID.randomUUID();
        CreateSectionRequest request = CreateSectionRequest.builder()
                .title("Test")
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        // Act & Assert
        assertThatThrownBy(() -> sectionService.createSection(courseId, request, instructorRole, differentInstructorId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("don't have permission");
    }

    @Test
    @DisplayName("Should calculate next order index correctly when sections exist")
    void testCreateSection_OrderIndexCalculation() {
        // Arrange
        CreateSectionRequest request = CreateSectionRequest.builder()
                .title("Advanced Topics")
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findMaxOrderIndexByCourseId(courseId)).thenReturn(2); // Existing sections: 0, 1, 2
        when(sectionRepository.save(any(CourseSection.class))).thenReturn(section);
        when(sectionRepository.countByCourseId(courseId)).thenReturn(4L);
        when(sectionMapper.toResponse(any(CourseSection.class)))
                .thenReturn(SectionResponse.builder().build());

        // Act
        sectionService.createSection(courseId, request, instructorRole, instructorId);

        // Assert
        verify(sectionRepository).save(argThat(s -> s.getOrderIndex() == 3));
    }

    // ==================== GET SECTIONS TESTS ====================

    @Test
    @DisplayName("Should get all sections for a course ordered by index")
    void testGetAllSectionsByCourse_Success() {
        // Arrange
        CourseSection section1 = CourseSection.builder().id(UUID.randomUUID()).orderIndex(0).build();
        CourseSection section2 = CourseSection.builder().id(UUID.randomUUID()).orderIndex(1).build();
        List<CourseSection> sections = Arrays.asList(section1, section2);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findByCourseIdOrderByOrderIndex(courseId)).thenReturn(sections);
        when(sectionMapper.toResponse(any(CourseSection.class)))
                .thenReturn(SectionResponse.builder().build());

        // Act
        List<SectionResponse> responses = sectionService.getAllSectionsByCourse(courseId);

        // Assert
        assertThat(responses).hasSize(2);
        verify(sectionRepository).findByCourseIdOrderByOrderIndex(courseId);
    }

    @Test
    @DisplayName("Should return empty list when course has no sections")
    void testGetAllSectionsByCourse_EmptyList() {
        // Arrange
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findByCourseIdOrderByOrderIndex(courseId)).thenReturn(Arrays.asList());

        // Act
        List<SectionResponse> responses = sectionService.getAllSectionsByCourse(courseId);

        // Assert
        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("Should get section by ID successfully")
    void testGetSectionById_Success() {
        // Arrange
        when(sectionRepository.findByIdWithCourse(sectionId)).thenReturn(Optional.of(section));
        when(sectionMapper.toResponse(section)).thenReturn(SectionResponse.builder().id(sectionId).build());

        // Act
        SectionResponse response = sectionService.getSectionById(courseId, sectionId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(sectionId);
    }

    @Test
    @DisplayName("Should throw exception when section not found")
    void testGetSectionById_NotFound() {
        // Arrange
        when(sectionRepository.findByIdWithCourse(sectionId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> sectionService.getSectionById(courseId, sectionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Section not found");
    }

    @Test
    @DisplayName("Should throw exception when section doesn't belong to course")
    void testGetSectionById_WrongCourse() {
        // Arrange
        UUID differentCourseId = UUID.randomUUID();
        when(sectionRepository.findByIdWithCourse(sectionId)).thenReturn(Optional.of(section));

        // Act & Assert
        assertThatThrownBy(() -> sectionService.getSectionById(differentCourseId, sectionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Section not found in the specified course");
    }



    // ==================== UPDATE SECTION TESTS ====================

    @Test
    @DisplayName("Should update section successfully as owner")
    void testUpdateSection_AsOwner_Success() {
        // Arrange
        UpdateSectionRequest request = UpdateSectionRequest.builder()
                .title("Updated Title")
                .description("Updated Description")
                .objective("Updated Objective")
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findByIdWithCourse(sectionId)).thenReturn(Optional.of(section));
        when(sectionRepository.save(any(CourseSection.class))).thenReturn(section);
        when(sectionMapper.toResponse(any(CourseSection.class)))
                .thenReturn(SectionResponse.builder().build());

        // Act
        SectionResponse response = sectionService.updateSection(courseId, sectionId, request, instructorRole, instructorId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(section.getTitle()).isEqualTo("Updated Title");
        assertThat(section.getDescription()).isEqualTo("Updated Description");
        assertThat(section.getObjective()).isEqualTo("Updated Objective");
        verify(sectionRepository).save(section);
    }

    @Test
    @DisplayName("Should update section successfully as admin")
    void testUpdateSection_AsAdmin_Success() {
        // Arrange
        UpdateSectionRequest request = UpdateSectionRequest.builder()
                .title("Updated Title")
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findByIdWithCourse(sectionId)).thenReturn(Optional.of(section));
        when(sectionRepository.save(any(CourseSection.class))).thenReturn(section);
        when(sectionMapper.toResponse(any(CourseSection.class)))
                .thenReturn(SectionResponse.builder().build());

        // Act
        SectionResponse response = sectionService.updateSection(courseId, sectionId, request, adminRole, adminId);

        // Assert
        assertThat(response).isNotNull();
        verify(sectionRepository).save(section);
    }

    @Test
    @DisplayName("Should update only provided fields")
    void testUpdateSection_PartialUpdate() {
        // Arrange
        UpdateSectionRequest request = UpdateSectionRequest.builder()
                .title("New Title")
                .build(); // Only title provided

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findByIdWithCourse(sectionId)).thenReturn(Optional.of(section));
        when(sectionRepository.save(any(CourseSection.class))).thenReturn(section);
        when(sectionMapper.toResponse(any(CourseSection.class)))
                .thenReturn(SectionResponse.builder().build());

        String originalDescription = section.getDescription();

        // Act
        sectionService.updateSection(courseId, sectionId, request, instructorRole, instructorId);

        // Assert
        assertThat(section.getTitle()).isEqualTo("New Title");
        assertThat(section.getDescription()).isEqualTo(originalDescription); // Unchanged
    }

    @Test
    @DisplayName("Should not update with blank title")
    void testUpdateSection_BlankTitleIgnored() {
        // Arrange
        UpdateSectionRequest request = UpdateSectionRequest.builder()
                .title("   ") // Blank title
                .description("Valid description")
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findByIdWithCourse(sectionId)).thenReturn(Optional.of(section));
        when(sectionRepository.save(any(CourseSection.class))).thenReturn(section);
        when(sectionMapper.toResponse(any(CourseSection.class)))
                .thenReturn(SectionResponse.builder().build());

        String originalTitle = section.getTitle();

        // Act
        sectionService.updateSection(courseId, sectionId, request, instructorRole, instructorId);

        // Assert
        assertThat(section.getTitle()).isEqualTo(originalTitle); // Title unchanged
        assertThat(section.getDescription()).isEqualTo("Valid description");
    }

    // ==================== DELETE SECTION TESTS ====================


    @Test
    @DisplayName("Should delete section and reorder remaining sections")
    void testDeleteSection_Success() {
        // Arrange
        CourseSection section2 = CourseSection.builder()
                .id(UUID.randomUUID())
                .course(course)
                .orderIndex(1)
                .build();

        CourseSection section3 = CourseSection.builder()
                .id(UUID.randomUUID())
                .course(course)
                .orderIndex(2)
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findByIdWithCourse(sectionId)).thenReturn(Optional.of(section));
        when(sectionRepository.findByCourseIdOrderByOrderIndex(courseId))
                .thenReturn(Arrays.asList(section2, section3));
        when(sectionRepository.countByCourseId(courseId)).thenReturn(2L);

        // Act
        sectionService.deleteSection(courseId, sectionId, instructorRole, instructorId);

        // Assert
        verify(sectionRepository).delete(section);
        verify(sectionRepository).saveAll(argThat(sections -> {
            List<CourseSection> sectionList = (List<CourseSection>) sections;
            return sectionList.size() == 2 &&
                    sectionList.get(0).getOrderIndex() == 0 &&
                    sectionList.get(1).getOrderIndex() == 1;
        }));
        verify(courseRepository, atLeastOnce()).save(course);
    }

    @Test
    @DisplayName("Should delete section as admin")
    void testDeleteSection_AsAdmin_Success() {
        // Arrange
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findByIdWithCourse(sectionId)).thenReturn(Optional.of(section));
        when(sectionRepository.findByCourseIdOrderByOrderIndex(courseId)).thenReturn(List.of());
        when(sectionRepository.countByCourseId(courseId)).thenReturn(0L);

        // Act
        sectionService.deleteSection(courseId, sectionId, adminRole, adminId);

        // Assert
        verify(sectionRepository).delete(section);
    }

    @Test
    @DisplayName("Should throw exception when deleting section not owned by instructor")
    void testDeleteSection_NotOwner() {
        // Arrange
        UUID differentInstructorId = UUID.randomUUID();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        // Act & Assert
        assertThatThrownBy(() -> sectionService.deleteSection(courseId, sectionId, instructorRole, differentInstructorId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("don't have permission");
    }

    // ==================== REORDER SECTIONS TESTS ====================

    @Test
    @DisplayName("Should reorder sections successfully")
    void testReorderSections_Success() {
        // Arrange
        CourseSection section1 = CourseSection.builder()
                .id(UUID.randomUUID())
                .course(course)
                .orderIndex(0)
                .build();

        CourseSection section2 = CourseSection.builder()
                .id(UUID.randomUUID())
                .course(course)
                .orderIndex(1)
                .build();

        ReorderSectionsRequest request = ReorderSectionsRequest.builder()
                .sectionOrders(Arrays.asList(
                        ReorderSectionsRequest.SectionOrder.builder()
                                .sectionId(section2.getId())
                                .orderIndex(0)
                                .build(),
                        ReorderSectionsRequest.SectionOrder.builder()
                                .sectionId(section1.getId())
                                .orderIndex(1)
                                .build()
                ))
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findAllById(anyList()))
                .thenReturn(Arrays.asList(section1, section2));
        when(sectionRepository.saveAll(anyList()))
                .thenReturn(Arrays.asList(section2, section1));
        when(sectionMapper.toResponse(any(CourseSection.class)))
                .thenReturn(SectionResponse.builder().build());

        // Act
        List<SectionResponse> responses = sectionService.reorderSections(courseId, request, instructorRole, instructorId);

        // Assert
        assertThat(responses).hasSize(2);
        verify(sectionRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Should reorder sections as admin")
    void testReorderSections_AsAdmin_Success() {
        // Arrange
        CourseSection section1 = CourseSection.builder()
                .id(UUID.randomUUID())
                .course(course)
                .orderIndex(0)
                .build();

        ReorderSectionsRequest request = ReorderSectionsRequest.builder()
                .sectionOrders(Arrays.asList(
                        ReorderSectionsRequest.SectionOrder.builder()
                                .sectionId(section1.getId())
                                .orderIndex(0)
                                .build()
                ))
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findAllById(anyList())).thenReturn(Arrays.asList(section1));
        when(sectionRepository.saveAll(anyList())).thenReturn(Arrays.asList(section1));
        when(sectionMapper.toResponse(any(CourseSection.class)))
                .thenReturn(SectionResponse.builder().build());

        // Act
        List<SectionResponse> responses = sectionService.reorderSections(courseId, request, adminRole, adminId);

        // Assert
        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("Should throw exception when reordering sections from different course")
    void testReorderSections_SectionNotInCourse() {
        // Arrange
        Course differentCourse = Course.builder()
                .id(UUID.randomUUID())
                .instructorId(instructorId)
                .build();

        CourseSection section1 = CourseSection.builder()
                .id(UUID.randomUUID())
                .course(differentCourse)
                .build();

        ReorderSectionsRequest request = ReorderSectionsRequest.builder()
                .sectionOrders(Arrays.asList(
                        ReorderSectionsRequest.SectionOrder.builder()
                                .sectionId(section1.getId())
                                .orderIndex(0)
                                .build()
                ))
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findAllById(anyList())).thenReturn(Arrays.asList(section1));

        // Act & Assert
        assertThatThrownBy(() -> sectionService.reorderSections(courseId, request, instructorRole, instructorId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("do not belong to this course");
    }

    @Test
    @DisplayName("Should throw exception when some sections not found during reorder")
    void testReorderSections_SomeNotFound() {
        // Arrange
        ReorderSectionsRequest request = ReorderSectionsRequest.builder()
                .sectionOrders(Arrays.asList(
                        ReorderSectionsRequest.SectionOrder.builder()
                                .sectionId(UUID.randomUUID())
                                .orderIndex(0)
                                .build(),
                        ReorderSectionsRequest.SectionOrder.builder()
                                .sectionId(UUID.randomUUID())
                                .orderIndex(1)
                                .build()
                ))
                .build();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(sectionRepository.findAllById(anyList())).thenReturn(Arrays.asList(section)); // Only 1 found

        // Act & Assert
        assertThatThrownBy(() -> sectionService.reorderSections(courseId, request, instructorRole, instructorId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("One or more sections not found");
    }
}