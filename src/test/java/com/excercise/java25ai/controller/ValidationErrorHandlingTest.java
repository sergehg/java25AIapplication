package com.excercise.java25ai.controller;

import com.excercise.java25ai.dto.CourseResponse;
import com.excercise.java25ai.dto.StudentResponse;
import com.excercise.java25ai.entity.enums.CourseStatus;
import com.excercise.java25ai.entity.enums.StudentStatus;
import com.excercise.java25ai.service.CourseService;
import com.excercise.java25ai.service.EnrollmentService;
import com.excercise.java25ai.service.StudentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice tests: invalid {@code @Valid} payloads must be rejected with HTTP 400 and the
 * uniform error body produced by the {@code @RestControllerAdvice}, without the request ever
 * reaching the service layer.
 */
@WebMvcTest({StudentController.class, CourseController.class, EnrollmentController.class})
class ValidationErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentService studentService;
    @MockitoBean
    private CourseService courseService;
    @MockitoBean
    private EnrollmentService enrollmentService;

    @Nested
    @DisplayName("student payloads")
    class StudentPayloads {

        @Test
        void rejectsEveryInvalidFieldAtOnceAndNamesEachOne() throws Exception {
            mockMvc.perform(post("/api/students")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"firstName":"","lastName":"  ","email":"not-an-email","dateOfBirth":"3000-01-01"}
                                     """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.path").value("/api/students"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.fieldErrors.firstName").value("First name is required"))
                    .andExpect(jsonPath("$.fieldErrors.lastName").value("Last name is required"))
                    .andExpect(jsonPath("$.fieldErrors.email").value("Email must be a well-formed address"))
                    .andExpect(jsonPath("$.fieldErrors.dateOfBirth").value("Date of birth must be in the past"));

            verifyNoInteractions(studentService);
        }

        @Test
        void rejectsMissingRequiredFields() throws Exception {
            mockMvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                    .andExpect(jsonPath("$.fieldErrors.lastName").exists())
                    .andExpect(jsonPath("$.fieldErrors.email").exists())
                    .andExpect(jsonPath("$.fieldErrors.dateOfBirth").value("Date of birth is required"));

            verifyNoInteractions(studentService);
        }

        @Test
        void rejectsFieldsThatExceedTheirMaximumLength() throws Exception {
            mockMvc.perform(post("/api/students")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"firstName":"%s","lastName":"Medina","email":"a@academy.io","dateOfBirth":"1990-05-17"}
                                     """.formatted("x".repeat(51))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.firstName").value("First name must not exceed 50 characters"));

            verifyNoInteractions(studentService);
        }

        @Test
        void rejectsAnInvalidUpdatePayload() throws Exception {
            mockMvc.perform(put("/api/students/{id}", 1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"firstName":"Jaime","lastName":"Medina","email":""}
                                     """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());

            verify(studentService, never()).update(any(), any());
        }

        /**
         * Control case: a valid payload must reach the service, proving the 400s above come from
         * validation rather than from a misconfigured endpoint.
         */
        @Test
        void acceptsAValidPayload() throws Exception {
            when(studentService.create(any())).thenReturn(new StudentResponse(
                    1L, "Jaime", "Medina", "Jaime Medina", "a@academy.io",
                    LocalDate.of(1990, 5, 17), 36, StudentStatus.ACTIVE, LocalDateTime.now()));

            mockMvc.perform(post("/api/students")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"firstName":"Jaime","lastName":"Medina","email":"a@academy.io","dateOfBirth":"1990-05-17"}
                                     """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.fieldErrors").doesNotExist());

            verify(studentService).create(any());
        }
    }

    @Nested
    @DisplayName("course payloads")
    class CoursePayloads {

        @Test
        void rejectsABlankCodeAndTitle() throws Exception {
            mockMvc.perform(post("/api/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"code":"","title":"","description":"ok","capacity":10}
                                     """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.code").value("Course code is required"))
                    .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"));

            verifyNoInteractions(courseService);
        }

        @Test
        void rejectsACapacityBelowOne() throws Exception {
            mockMvc.perform(post("/api/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"code":"JAVA-25","title":"Programming","description":"Java","capacity":0}
                                     """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.capacity").value("Capacity must be at least 1"));

            verifyNoInteractions(courseService);
        }

        @Test
        void rejectsACapacityAboveTheMaximum() throws Exception {
            mockMvc.perform(post("/api/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"code":"JAVA-25","title":"Programming","description":"Java","capacity":501}
                                     """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.capacity").value("Capacity must not exceed 500"));

            verifyNoInteractions(courseService);
        }

        @Test
        void rejectsAMissingCapacity() throws Exception {
            mockMvc.perform(post("/api/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"code":"JAVA-25","title":"Programming","description":"Java"}
                                     """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.capacity").value("Capacity is required"));

            verifyNoInteractions(courseService);
        }

        @Test
        void rejectsAnOversizedDescription() throws Exception {
            mockMvc.perform(post("/api/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"code":"JAVA-25","title":"Programming","description":"%s","capacity":10}
                                     """.formatted("x".repeat(501))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.description").exists());

            verifyNoInteractions(courseService);
        }

        @Test
        void rejectsAnInvalidUpdatePayload() throws Exception {
            mockMvc.perform(put("/api/courses/{id}", 1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"title":"","description":"Java","capacity":0}
                                     """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.path").value("/api/courses/1"))
                    .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
                    .andExpect(jsonPath("$.fieldErrors.capacity").value("Capacity must be at least 1"));

            verify(courseService, never()).update(any(), any());
        }

        @Test
        void acceptsAValidPayload() throws Exception {
            when(courseService.create(any())).thenReturn(new CourseResponse(
                    1L, "JAVA-25", "Programming", "Java", 10, 0L, 10L, CourseStatus.OPEN));

            mockMvc.perform(post("/api/courses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"code":"JAVA-25","title":"Programming","description":"Java","capacity":10}
                                     """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value("JAVA-25"));
        }
    }

    @Nested
    @DisplayName("enrollment payloads")
    class EnrollmentPayloads {

        @Test
        void rejectsMissingIdentifiersAndClassroom() throws Exception {
            mockMvc.perform(post("/api/enrollments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.studentId").value("Student id is required"))
                    .andExpect(jsonPath("$.fieldErrors.courseId").value("Course id is required"))
                    .andExpect(jsonPath("$.fieldErrors.classroom").value("Classroom is required"));

            verifyNoInteractions(enrollmentService);
        }

        /**
         * An unparseable enum never reaches Bean Validation; Jackson fails first, so this is a
         * malformed body rather than a field error.
         */
        @Test
        void rejectsAnUnknownClassroomAsAMalformedBody() throws Exception {
            mockMvc.perform(post("/api/enrollments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                     {"studentId":1,"courseId":2,"classroom":"Z"}
                                     """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Malformed request body"))
                    .andExpect(jsonPath("$.fieldErrors").doesNotExist());

            verifyNoInteractions(enrollmentService);
        }

        @Test
        void rejectsASyntacticallyInvalidBody() throws Exception {
            mockMvc.perform(post("/api/enrollments").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Malformed request body"));

            verifyNoInteractions(enrollmentService);
        }
    }
}
