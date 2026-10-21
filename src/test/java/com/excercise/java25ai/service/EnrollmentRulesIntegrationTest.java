package com.excercise.java25ai.service;

import com.excercise.java25ai.dto.CreateCourseRequest;
import com.excercise.java25ai.dto.CreateStudentRequest;
import com.excercise.java25ai.dto.EnrollStudentRequest;
import com.excercise.java25ai.dto.UpdateCourseRequest;
import com.excercise.java25ai.entity.enums.Classroom;
import com.excercise.java25ai.entity.enums.CourseStatus;
import com.excercise.java25ai.entity.enums.EnrollmentStatus;
import com.excercise.java25ai.exception.AlreadyEnrolledException;
import com.excercise.java25ai.exception.BusinessRuleViolationException;
import com.excercise.java25ai.exception.CourseFullException;
import com.excercise.java25ai.repository.CourseRepository;
import com.excercise.java25ai.repository.EnrollmentRepository;
import com.excercise.java25ai.repository.StudentRepository;



import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression cover for the enroll() guard ordering.
 */
@SpringBootTest
class EnrollmentRulesIntegrationTest {

    @Autowired StudentService studentService;
    @Autowired CourseService courseService;
    @Autowired EnrollmentService enrollmentService;
    @Autowired StudentRepository studentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired EnrollmentRepository enrollmentRepository;

    @BeforeEach
    void clean() {
        enrollmentRepository.deleteAll();
        studentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private Long student(String email) {
        return studentService.create(new CreateStudentRequest(
                "Jaime", "Medina", email, LocalDate.of(1990, 5, 17))).id();
    }

    private Long course(String code, int capacity) {
        return courseService.create(new CreateCourseRequest(code, "Programming", "Java", capacity)).id();
    }

    @Test
    void dropThenReEnrollReusesTheSameRowAndReopensTheCourse() {
        var c = course("JAVA-25", 1);
        var s = student("a@academy.io");

        var first = enrollmentService.enroll(new EnrollStudentRequest(s, c, Classroom.A));
        assertThat(courseService.getById(c).status()).isEqualTo(CourseStatus.CLOSED);

        enrollmentService.drop(first.id());
        assertThat(courseService.getById(c).status()).isEqualTo(CourseStatus.OPEN);

        var again = enrollmentService.enroll(new EnrollStudentRequest(s, c, Classroom.C));
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.classroom()).isEqualTo(Classroom.C);
        assertThat(again.status()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(enrollmentRepository.count()).isEqualTo(1);
    }

    @Test
    void alreadyEnrolledWinsOverCourseFullForTheSameStudent() {
        var c = course("JAVA-25", 1);
        var s = student("a@academy.io");
        enrollmentService.enroll(new EnrollStudentRequest(s, c, Classroom.A));

        assertThatThrownBy(() -> enrollmentService.enroll(new EnrollStudentRequest(s, c, Classroom.B)))
                .isInstanceOf(AlreadyEnrolledException.class);
    }

    @Test
    void aDroppedStudentCannotReclaimASeatSomeoneElseTook() {
        var c = course("JAVA-25", 1);
        var first = student("a@academy.io");
        var second = student("b@academy.io");

        var e = enrollmentService.enroll(new EnrollStudentRequest(first, c, Classroom.A));
        enrollmentService.drop(e.id());
        enrollmentService.enroll(new EnrollStudentRequest(second, c, Classroom.B));

        assertThatThrownBy(() -> enrollmentService.enroll(new EnrollStudentRequest(first, c, Classroom.C)))
                .isInstanceOf(CourseFullException.class);
    }

    @Test
    void completedEnrollmentCannotBeReEnrolled() {
        var c = course("JAVA-25", 10);
        var s = student("a@academy.io");
        var e = enrollmentService.enroll(new EnrollStudentRequest(s, c, Classroom.A));

        enrollmentService.complete(e.id());
        assertThatThrownBy(() -> enrollmentService.enroll(new EnrollStudentRequest(s, c, Classroom.A)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("completed");
    }

    @Test
    void archivedCourseRejectsEnrollmentBeforeAnyCapacityConsideration() {
        var c = course("JAVA-25", 1);
        courseService.archive(c);

        assertThatThrownBy(() -> enrollmentService.enroll(
                new EnrollStudentRequest(student("a@academy.io"), c, Classroom.A)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("archived");
    }

    @Test
    void capacityCannotBeReducedBelowActiveEnrollments() {
        var c = course("JAVA-25", 10);
        enrollmentService.enroll(new EnrollStudentRequest(student("a@academy.io"), c, Classroom.A));
        enrollmentService.enroll(new EnrollStudentRequest(student("b@academy.io"), c, Classroom.B));

        assertThatThrownBy(() -> courseService.update(c, new UpdateCourseRequest("T", "D", 1)))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(courseService.update(c, new UpdateCourseRequest("T", "D", 2)).status())
                .isEqualTo(CourseStatus.CLOSED);
    }
}
