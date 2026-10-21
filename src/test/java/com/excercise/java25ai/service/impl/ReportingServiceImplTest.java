package com.excercise.java25ai.service.impl;

import com.excercise.java25ai.dto.StudentResponse;
import com.excercise.java25ai.entity.CourseEntity;
import com.excercise.java25ai.entity.EnrollmentEntity;
import com.excercise.java25ai.entity.StudentEntity;
import com.excercise.java25ai.entity.enums.Classroom;
import com.excercise.java25ai.entity.enums.EnrollmentStatus;
import com.excercise.java25ai.entity.enums.StudentStatus;
import com.excercise.java25ai.mapper.StudentMapper;
import com.excercise.java25ai.repository.EnrollmentRepository;
import com.excercise.java25ai.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the functional-programming reports in {@link ReportingServiceImpl}: the
 * descending age ordering and the shape of the course-to-students grouping.
 */
@ExtendWith(MockitoExtension.class)
class ReportingServiceImplTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;

    private ReportingServiceImpl reportingService;

    @BeforeEach
    void setUp() {
        reportingService = new ReportingServiceImpl(studentRepository, enrollmentRepository, new StudentMapper());
    }

    private static StudentEntity student(long id, String firstName, String lastName, int birthYear) {
        return StudentEntity.builder()
                .id(id)
                .firstName(firstName)
                .lastName(lastName)
                .email(firstName.toLowerCase() + "@academy.io")
                .dateOfBirth(LocalDate.of(birthYear, 1, 1))
                .status(StudentStatus.ACTIVE)
                .build();
    }

    private static CourseEntity course(long id, String code, String title) {
        return CourseEntity.builder().id(id).code(code).title(title).capacity(30).build();
    }

    private static EnrollmentEntity enrolledIn(CourseEntity course, StudentEntity student) {
        return EnrollmentEntity.builder()
                .id(student.getId() * 100 + course.getId())
                .student(student)
                .course(course)
                .status(EnrollmentStatus.ENROLLED)
                .classroom(Classroom.A)
                .build();
    }

    @Nested
    @DisplayName("getStudentsSortedByAgeDesc")
    class AgeSorting {

        @Test
        void ordersStudentsFromOldestToYoungest() {
            when(studentRepository.findAll()).thenReturn(List.of(
                    student(1L, "Young", "Alpha", 2005),
                    student(2L, "Old", "Beta", 1975),
                    student(3L, "Middle", "Gamma", 1990)));

            var sorted = reportingService.getStudentsSortedByAgeDesc();

            assertThat(sorted).extracting(StudentResponse::id).containsExactly(2L, 3L, 1L);
            assertThat(sorted).extracting(StudentResponse::age)
                    .isSortedAccordingTo((left, right) -> Integer.compare(right, left));
        }

        /**
         * Equal ages must not produce an arbitrary order, otherwise the report changes between
         * runs for the same data.
         */
        @Test
        void breaksTiesDeterministicallyByLastThenFirstName() {
            when(studentRepository.findAll()).thenReturn(List.of(
                    student(1L, "Zoe", "Medina", 1990),
                    student(2L, "Ana", "Medina", 1990),
                    student(3L, "Ana", "Lopez", 1990)));

            var sorted = reportingService.getStudentsSortedByAgeDesc();

            assertThat(sorted).extracting(StudentResponse::fullName)
                    .containsExactly("Ana Lopez", "Ana Medina", "Zoe Medina");
        }

        @Test
        void returnsAnEmptyListWhenThereAreNoStudents() {
            when(studentRepository.findAll()).thenReturn(List.of());

            assertThat(reportingService.getStudentsSortedByAgeDesc()).isEmpty();
        }

        @Test
        void derivesAgeFromTheBirthDate() {
            var born1990 = student(1L, "Jaime", "Medina", 1990);
            when(studentRepository.findAll()).thenReturn(List.of(born1990));

            var expectedAge = java.time.Period.between(LocalDate.of(1990, 1, 1), LocalDate.now()).getYears();

            assertThat(reportingService.getStudentsSortedByAgeDesc())
                    .singleElement()
                    .extracting(StudentResponse::age)
                    .isEqualTo(expectedAge);
        }
    }

    @Nested
    @DisplayName("getEnrolledStudentsByCourse")
    class CourseToStudentGrouping {

        @Test
        void groupsStudentNamesUnderTheirCourseTitle() {
            var programming = course(1L, "JAVA-25", "Programming");
            var database = course(2L, "DB-101", "Database");
            when(enrollmentRepository.findAllByStatusWithStudentAndCourse(EnrollmentStatus.ENROLLED))
                    .thenReturn(List.of(
                            enrolledIn(programming, student(1L, "Jaime", "Medina", 1990)),
                            enrolledIn(database, student(2L, "Mito", "X", 1995)),
                            enrolledIn(programming, student(3L, "Ana", "Lopez", 2000))));

            Map<String, List<String>> grouped = reportingService.getEnrolledStudentsByCourse();

            assertThat(grouped).hasSize(2);
            assertThat(grouped.get("Programming")).containsExactly("Ana Lopez", "Jaime Medina");
            assertThat(grouped.get("Database")).containsExactly("Mito X");
        }

        /**
         * Only active enrollments belong in the report, which the service delegates to the
         * repository by querying on {@code ENROLLED}.
         */
        @Test
        void queriesOnlyActiveEnrollments() {
            when(enrollmentRepository.findAllByStatusWithStudentAndCourse(EnrollmentStatus.ENROLLED))
                    .thenReturn(List.of());

            reportingService.getEnrolledStudentsByCourse();

            verify(enrollmentRepository).findAllByStatusWithStudentAndCourse(EnrollmentStatus.ENROLLED);
        }

        @Test
        void keysAreOrderedAlphabetically() {
            var zoology = course(1L, "ZOO-1", "Zoology");
            var algebra = course(2L, "ALG-1", "Algebra");
            when(enrollmentRepository.findAllByStatusWithStudentAndCourse(EnrollmentStatus.ENROLLED))
                    .thenReturn(List.of(
                            enrolledIn(zoology, student(1L, "Jaime", "Medina", 1990)),
                            enrolledIn(algebra, student(2L, "Ana", "Lopez", 1995))));

            var grouped = reportingService.getEnrolledStudentsByCourse();

            assertThat(grouped).isInstanceOf(TreeMap.class);
            assertThat(grouped.keySet()).containsExactly("Algebra", "Zoology");
        }

        @Test
        void returnsAnEmptyMapWhenNobodyIsEnrolled() {
            when(enrollmentRepository.findAllByStatusWithStudentAndCourse(EnrollmentStatus.ENROLLED))
                    .thenReturn(List.of());

            assertThat(reportingService.getEnrolledStudentsByCourse()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getEnrolledStudentsByCourseReport")
    class TextReport {

        @Test
        void rendersTheFormatGivenInTheBusinessRules() {
            var programming = course(1L, "JAVA-25", "Programming");
            var database = course(2L, "DB-101", "Database");
            when(enrollmentRepository.findAllByStatusWithStudentAndCourse(EnrollmentStatus.ENROLLED))
                    .thenReturn(List.of(
                            enrolledIn(programming, student(1L, "Jaime", "Medina", 1990)),
                            enrolledIn(database, student(2L, "Mito", "X", 1995))));

            assertThat(reportingService.getEnrolledStudentsByCourseReport())
                    .isEqualTo("""
                               Database
                                 - Mito X
                               Programming
                                 - Jaime Medina""");
        }

        @Test
        void rendersAnEmptyStringWhenNobodyIsEnrolled() {
            when(enrollmentRepository.findAllByStatusWithStudentAndCourse(EnrollmentStatus.ENROLLED))
                    .thenReturn(List.of());

            assertThat(reportingService.getEnrolledStudentsByCourseReport()).isEmpty();
        }
    }
}
