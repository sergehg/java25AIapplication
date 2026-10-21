package com.excercise.java25ai.service.impl;

import com.excercise.java25ai.dto.EnrollStudentRequest;
import com.excercise.java25ai.entity.CourseEntity;
import com.excercise.java25ai.entity.EnrollmentEntity;
import com.excercise.java25ai.entity.StudentEntity;
import com.excercise.java25ai.entity.enums.Classroom;
import com.excercise.java25ai.entity.enums.CourseStatus;
import com.excercise.java25ai.entity.enums.EnrollmentStatus;
import com.excercise.java25ai.entity.enums.StudentStatus;
import com.excercise.java25ai.exception.AlreadyEnrolledException;
import com.excercise.java25ai.exception.BusinessRuleViolationException;
import com.excercise.java25ai.exception.CourseFullException;
import com.excercise.java25ai.exception.ResourceNotFoundException;
import com.excercise.java25ai.mapper.EnrollmentMapper;
import com.excercise.java25ai.repository.CourseRepository;
import com.excercise.java25ai.repository.EnrollmentRepository;
import com.excercise.java25ai.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EnrollmentServiceImpl}, covering the enrollment rules and the order in
 * which its guards fire.
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceImplTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private CourseRepository courseRepository;

    private EnrollmentServiceImpl enrollmentService;

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentServiceImpl(
                enrollmentRepository, studentRepository, courseRepository, new EnrollmentMapper());
    }

    private static StudentEntity student(StudentStatus status) {
        return StudentEntity.builder()
                .id(1L)
                .firstName("Jaime")
                .lastName("Medina")
                .email("a@academy.io")
                .dateOfBirth(LocalDate.of(1990, 5, 17))
                .status(status)
                .build();
    }

    private static CourseEntity course(int capacity, CourseStatus status) {
        return CourseEntity.builder()
                .id(2L)
                .code("JAVA-25")
                .title("Programming")
                .capacity(capacity)
                .status(status)
                .build();
    }

    private static EnrollmentEntity enrollment(EnrollmentStatus status, Classroom classroom) {
        return EnrollmentEntity.builder()
                .id(3L)
                .student(student(StudentStatus.ACTIVE))
                .course(course(10, CourseStatus.OPEN))
                .status(status)
                .classroom(classroom)
                .enrolledAt(LocalDateTime.of(2026, 1, 1, 9, 0))
                .build();
    }

    private static EnrollStudentRequest request(Classroom classroom) {
        return new EnrollStudentRequest(1L, 2L, classroom);
    }

    @Nested
    @DisplayName("enroll")
    class Enroll {

        @Test
        void throwsWhenTheStudentDoesNotExist() {
            when(studentRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.enroll(request(Classroom.A)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Student not found");
        }

        @Test
        void rejectsAStudentWhoIsNotActive() {
            when(studentRepository.findById(1L)).thenReturn(Optional.of(student(StudentStatus.INACTIVE)));

            assertThatThrownBy(() -> enrollmentService.enroll(request(Classroom.A)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("ACTIVE");

            verify(courseRepository, never()).findByIdForUpdate(any());
        }

        @Test
        void throwsWhenTheCourseDoesNotExist() {
            when(studentRepository.findById(1L)).thenReturn(Optional.of(student(StudentStatus.ACTIVE)));
            when(courseRepository.findByIdForUpdate(2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.enroll(request(Classroom.A)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Course not found");
        }

        @Test
        void rejectsAnArchivedCourseBeforeConsideringCapacity() {
            when(studentRepository.findById(1L)).thenReturn(Optional.of(student(StudentStatus.ACTIVE)));
            when(courseRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(course(10, CourseStatus.ARCHIVED)));

            assertThatThrownBy(() -> enrollmentService.enroll(request(Classroom.A)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("archived");

            verify(enrollmentRepository, never()).countByCourseIdAndStatus(any(), any());
        }

        /**
         * A student who already holds a seat must hear "already enrolled" rather than "course
         * full", so the duplicate check has to run before the capacity check.
         */
        @Test
        void reportsAlreadyEnrolledEvenWhenTheCourseIsFull() {
            when(studentRepository.findById(1L)).thenReturn(Optional.of(student(StudentStatus.ACTIVE)));
            when(courseRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(course(1, CourseStatus.CLOSED)));
            when(enrollmentRepository.findByStudentIdAndCourseId(1L, 2L))
                    .thenReturn(Optional.of(enrollment(EnrollmentStatus.ENROLLED, Classroom.A)));

            assertThatThrownBy(() -> enrollmentService.enroll(request(Classroom.B)))
                    .isInstanceOf(AlreadyEnrolledException.class);

            verify(enrollmentRepository, never()).saveAndFlush(any());
        }

        @Test
        void rejectsReEnrollmentIntoACompletedCourse() {
            when(studentRepository.findById(1L)).thenReturn(Optional.of(student(StudentStatus.ACTIVE)));
            when(courseRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(course(10, CourseStatus.OPEN)));
            when(enrollmentRepository.findByStudentIdAndCourseId(1L, 2L))
                    .thenReturn(Optional.of(enrollment(EnrollmentStatus.COMPLETED, Classroom.A)));

            assertThatThrownBy(() -> enrollmentService.enroll(request(Classroom.B)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("completed");
        }

        @Test
        void rejectsAFullCourseForANewStudent() {
            when(studentRepository.findById(1L)).thenReturn(Optional.of(student(StudentStatus.ACTIVE)));
            when(courseRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(course(2, CourseStatus.OPEN)));
            when(enrollmentRepository.findByStudentIdAndCourseId(1L, 2L)).thenReturn(Optional.empty());
            when(enrollmentRepository.countByCourseIdAndStatus(2L, EnrollmentStatus.ENROLLED)).thenReturn(2L);

            assertThatThrownBy(() -> enrollmentService.enroll(request(Classroom.A)))
                    .isInstanceOf(CourseFullException.class)
                    .hasMessageContaining("capacity of 2");

            verify(enrollmentRepository, never()).saveAndFlush(any());
        }

        @Test
        void rejectsACourseThatIsNeitherOpenNorFull() {
            when(studentRepository.findById(1L)).thenReturn(Optional.of(student(StudentStatus.ACTIVE)));
            when(courseRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(course(10, CourseStatus.CLOSED)));
            when(enrollmentRepository.findByStudentIdAndCourseId(1L, 2L)).thenReturn(Optional.empty());
            when(enrollmentRepository.countByCourseIdAndStatus(2L, EnrollmentStatus.ENROLLED)).thenReturn(1L);

            assertThatThrownBy(() -> enrollmentService.enroll(request(Classroom.A)))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("not open");
        }

        @Test
        void createsAnEnrollmentAndLeavesTheCourseOpenWhileSeatsRemain() {
            var courseEntity = course(10, CourseStatus.OPEN);
            when(studentRepository.findById(1L)).thenReturn(Optional.of(student(StudentStatus.ACTIVE)));
            when(courseRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(courseEntity));
            when(enrollmentRepository.findByStudentIdAndCourseId(1L, 2L)).thenReturn(Optional.empty());
            when(enrollmentRepository.countByCourseIdAndStatus(2L, EnrollmentStatus.ENROLLED)).thenReturn(3L);
            when(enrollmentRepository.saveAndFlush(any(EnrollmentEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            var response = enrollmentService.enroll(request(Classroom.B));

            assertThat(response.status()).isEqualTo(EnrollmentStatus.ENROLLED);
            assertThat(response.classroom()).isEqualTo(Classroom.B);
            assertThat(response.courseCode()).isEqualTo("JAVA-25");
            assertThat(response.studentName()).isEqualTo("Jaime Medina");
            assertThat(courseEntity.getStatus()).isEqualTo(CourseStatus.OPEN);
        }

        @Test
        void closesTheCourseWhenTheLastSeatIsTaken() {
            var courseEntity = course(4, CourseStatus.OPEN);
            when(studentRepository.findById(1L)).thenReturn(Optional.of(student(StudentStatus.ACTIVE)));
            when(courseRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(courseEntity));
            when(enrollmentRepository.findByStudentIdAndCourseId(1L, 2L)).thenReturn(Optional.empty());
            when(enrollmentRepository.countByCourseIdAndStatus(2L, EnrollmentStatus.ENROLLED)).thenReturn(3L);
            when(enrollmentRepository.saveAndFlush(any(EnrollmentEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            enrollmentService.enroll(request(Classroom.A));

            assertThat(courseEntity.getStatus()).isEqualTo(CourseStatus.CLOSED);
        }

        @Test
        void reactivatesADroppedEnrollmentInPlaceInsteadOfInsertingASecondRow() {
            var existing = enrollment(EnrollmentStatus.DROPPED, Classroom.A);
            when(studentRepository.findById(1L)).thenReturn(Optional.of(student(StudentStatus.ACTIVE)));
            when(courseRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(course(10, CourseStatus.OPEN)));
            when(enrollmentRepository.findByStudentIdAndCourseId(1L, 2L)).thenReturn(Optional.of(existing));
            when(enrollmentRepository.countByCourseIdAndStatus(2L, EnrollmentStatus.ENROLLED)).thenReturn(0L);
            when(enrollmentRepository.saveAndFlush(existing)).thenReturn(existing);

            var response = enrollmentService.enroll(request(Classroom.C));

            assertThat(response.id()).isEqualTo(3L);
            assertThat(existing.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
            assertThat(existing.getClassroom()).isEqualTo(Classroom.C);
            assertThat(existing.getEnrolledAt()).isAfter(LocalDateTime.of(2026, 1, 1, 9, 0));
            verify(enrollmentRepository).saveAndFlush(existing);
        }
    }

    @Nested
    @DisplayName("status transitions")
    class StatusTransitions {

        @Test
        void dropMarksTheRecordDroppedAndReopensTheCourse() {
            var existing = enrollment(EnrollmentStatus.ENROLLED, Classroom.A);
            var courseEntity = CourseEntity.builder()
                    .id(2L).code("JAVA-25").title("Programming").capacity(1).status(CourseStatus.CLOSED).build();
            when(enrollmentRepository.findById(3L)).thenReturn(Optional.of(existing));
            when(courseRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(courseEntity));
            when(enrollmentRepository.countByCourseIdAndStatus(2L, EnrollmentStatus.ENROLLED)).thenReturn(0L);

            var response = enrollmentService.drop(3L);

            assertThat(response.status()).isEqualTo(EnrollmentStatus.DROPPED);
            assertThat(courseEntity.getStatus()).isEqualTo(CourseStatus.OPEN);
        }

        @Test
        void completeMarksTheRecordCompleted() {
            var existing = enrollment(EnrollmentStatus.ENROLLED, Classroom.A);
            var courseEntity = course(10, CourseStatus.OPEN);
            when(enrollmentRepository.findById(3L)).thenReturn(Optional.of(existing));
            when(courseRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(courseEntity));
            when(enrollmentRepository.countByCourseIdAndStatus(2L, EnrollmentStatus.ENROLLED)).thenReturn(0L);

            assertThat(enrollmentService.complete(3L).status()).isEqualTo(EnrollmentStatus.COMPLETED);
        }

        @Test
        void onlyAnActiveEnrollmentCanTransition() {
            when(enrollmentRepository.findById(3L))
                    .thenReturn(Optional.of(enrollment(EnrollmentStatus.DROPPED, Classroom.A)));

            assertThatThrownBy(() -> enrollmentService.complete(3L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("only an ENROLLED record");

            verify(enrollmentRepository, never()).saveAndFlush(any());
        }

        @Test
        void throwsWhenTheEnrollmentDoesNotExist() {
            when(enrollmentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.drop(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Enrollment not found");
        }
    }

    @Nested
    @DisplayName("lookups")
    class Lookups {

        @Test
        void findByStudentRejectsAnUnknownStudent() {
            when(studentRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> enrollmentService.findByStudent(99L, PageRequest.of(0, 10)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Student not found");
        }

        @Test
        void findByCourseRejectsAnUnknownCourse() {
            when(courseRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> enrollmentService.findByCourse(99L, PageRequest.of(0, 10)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Course not found");
        }
    }
}
