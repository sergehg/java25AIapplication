package com.excercise.java25ai.service.impl;

import com.excercise.java25ai.dto.CreateCourseRequest;
import com.excercise.java25ai.dto.UpdateCourseRequest;
import com.excercise.java25ai.entity.CourseEntity;
import com.excercise.java25ai.entity.enums.CourseStatus;
import com.excercise.java25ai.entity.enums.EnrollmentStatus;
import com.excercise.java25ai.exception.BusinessRuleViolationException;
import com.excercise.java25ai.exception.DuplicateResourceException;
import com.excercise.java25ai.exception.ResourceNotFoundException;
import com.excercise.java25ai.mapper.CourseMapper;
import com.excercise.java25ai.repository.CourseRepository;
import com.excercise.java25ai.repository.EnrollmentRepository;
import com.excercise.java25ai.repository.projection.CourseEnrollmentCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CourseServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;

    private CourseServiceImpl courseService;

    @BeforeEach
    void setUp() {
        courseService = new CourseServiceImpl(courseRepository, enrollmentRepository, new CourseMapper());
    }

    private static CourseEntity course(Long id, int capacity, CourseStatus status) {
        return CourseEntity.builder()
                .id(id)
                .code("JAVA-25")
                .title("Programming")
                .description("Java")
                .capacity(capacity)
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void savesTheCourseAsOpenWithEverySeatAvailable() {
            when(courseRepository.existsByCodeIgnoreCase("JAVA-25")).thenReturn(false);
            when(courseRepository.save(any(CourseEntity.class))).thenAnswer(invocation -> {
                CourseEntity toSave = invocation.getArgument(0);
                toSave.setId(1L);
                return toSave;
            });

            var response = courseService.create(new CreateCourseRequest("JAVA-25", "Programming", "Java", 30));

            assertThat(response.status()).isEqualTo(CourseStatus.OPEN);
            assertThat(response.activeEnrollments()).isZero();
            assertThat(response.availableSeats()).isEqualTo(30);
        }

        @Test
        void rejectsADuplicateCodeCaseInsensitively() {
            when(courseRepository.existsByCodeIgnoreCase("java-25")).thenReturn(true);

            assertThatThrownBy(() -> courseService.create(
                    new CreateCourseRequest("java-25", "Programming", "Java", 30)))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("code");

            verify(courseRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("read")
    class Read {

        @Test
        void getByIdReportsLiveOccupancy() {
            when(courseRepository.findById(1L)).thenReturn(Optional.of(course(1L, 10, CourseStatus.OPEN)));
            when(enrollmentRepository.countByCourseIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(4L);

            var response = courseService.getById(1L);

            assertThat(response.activeEnrollments()).isEqualTo(4);
            assertThat(response.availableSeats()).isEqualTo(6);
        }

        @Test
        void getByIdThrowsWhenAbsent() {
            when(courseRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> courseService.getById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Course not found");
        }

        @Test
        void getByCodeResolvesCaseInsensitively() {
            when(courseRepository.findByCodeIgnoreCase("java-25"))
                    .thenReturn(Optional.of(course(1L, 10, CourseStatus.OPEN)));
            when(enrollmentRepository.countByCourseIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(0L);

            assertThat(courseService.getByCode("java-25").code()).isEqualTo("JAVA-25");
        }

        /**
         * The listing path must resolve occupancy with one batched query, never one count per row.
         */
        @Test
        void findAllResolvesOccupancyInASingleBatchedQuery() {
            Pageable pageable = PageRequest.of(0, 10);
            when(courseRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(course(1L, 10, CourseStatus.OPEN), course(2L, 5, CourseStatus.OPEN))));
            when(enrollmentRepository.countByCourseIdInAndStatus(List.of(1L, 2L), EnrollmentStatus.ENROLLED))
                    .thenReturn(List.of(new CourseEnrollmentCount(1L, 3L)));

            var page = courseService.findAll(null, pageable);

            assertThat(page.getContent()).extracting("activeEnrollments").containsExactly(3L, 0L);
            verify(enrollmentRepository, never()).countByCourseIdAndStatus(anyLong(), any());
        }

        @Test
        void findOpenCoursesFiltersOnOpenStatus() {
            when(courseRepository.findByStatus(CourseStatus.OPEN))
                    .thenReturn(List.of(course(1L, 10, CourseStatus.OPEN)));
            when(enrollmentRepository.countByCourseIdInAndStatus(List.of(1L), EnrollmentStatus.ENROLLED))
                    .thenReturn(List.of());

            assertThat(courseService.findOpenCourses()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void rejectsACapacityBelowTheActiveEnrollmentCount() {
            var existing = course(1L, 10, CourseStatus.OPEN);
            when(courseRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(enrollmentRepository.countByCourseIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(5L);

            assertThatThrownBy(() -> courseService.update(1L, new UpdateCourseRequest("New", "Desc", 4)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("currently enrolled");

            assertThat(existing.getCapacity()).isEqualTo(10);
            assertThat(existing.getTitle()).isEqualTo("Programming");
        }

        @Test
        void closesTheCourseWhenTheNewCapacityExactlyMatchesTheActiveCount() {
            var existing = course(1L, 10, CourseStatus.OPEN);
            when(courseRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(enrollmentRepository.countByCourseIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(5L);

            var response = courseService.update(1L, new UpdateCourseRequest("New", "Desc", 5));

            assertThat(response.status()).isEqualTo(CourseStatus.CLOSED);
            assertThat(response.availableSeats()).isZero();
        }

        @Test
        void reopensAClosedCourseWhenCapacityIsRaised() {
            var existing = course(1L, 5, CourseStatus.CLOSED);
            when(courseRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(enrollmentRepository.countByCourseIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(5L);

            var response = courseService.update(1L, new UpdateCourseRequest("New", "Desc", 10));

            assertThat(response.status()).isEqualTo(CourseStatus.OPEN);
            assertThat(response.availableSeats()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("deletion")
    class Deletion {

        @Test
        void archiveIsRejectedWhileActiveEnrollmentsRemain() {
            when(courseRepository.findById(1L)).thenReturn(Optional.of(course(1L, 10, CourseStatus.OPEN)));
            when(enrollmentRepository.existsByCourseIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(true);

            assertThatThrownBy(() -> courseService.archive(1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("cannot be archived");
        }

        @Test
        void archiveIsASoftDeleteThatKeepsTheRow() {
            var existing = course(1L, 10, CourseStatus.OPEN);
            when(courseRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(enrollmentRepository.existsByCourseIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(false);
            when(enrollmentRepository.countByCourseIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(0L);

            assertThat(courseService.archive(1L).status()).isEqualTo(CourseStatus.ARCHIVED);
            assertThat(existing.getStatus()).isEqualTo(CourseStatus.ARCHIVED);
            verify(courseRepository, never()).delete(any());
        }

        @Test
        void hardDeleteIsRejectedWhileActiveEnrollmentsRemain() {
            when(courseRepository.findById(1L)).thenReturn(Optional.of(course(1L, 10, CourseStatus.OPEN)));
            when(enrollmentRepository.existsByCourseIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(true);

            assertThatThrownBy(() -> courseService.delete(1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("cannot be deleted");

            verify(courseRepository, never()).delete(any());
        }

        @Test
        void hardDeleteIsRejectedWhenOnlyHistoricalEnrollmentsRemain() {
            when(courseRepository.findById(1L)).thenReturn(Optional.of(course(1L, 10, CourseStatus.OPEN)));
            when(enrollmentRepository.existsByCourseIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(false);
            when(enrollmentRepository.existsByCourseId(1L)).thenReturn(true);

            assertThatThrownBy(() -> courseService.delete(1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("archive it instead");

            verify(courseRepository, never()).delete(any());
        }

        @Test
        void hardDeleteProceedsForACourseWithNoEnrollments() {
            var existing = course(1L, 10, CourseStatus.OPEN);
            when(courseRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(enrollmentRepository.existsByCourseIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(false);
            when(enrollmentRepository.existsByCourseId(1L)).thenReturn(false);

            courseService.delete(1L);

            verify(courseRepository).delete(existing);
        }
    }
}
