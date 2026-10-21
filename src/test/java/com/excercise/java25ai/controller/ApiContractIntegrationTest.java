package com.excercise.java25ai.controller;

import com.excercise.java25ai.repository.CourseRepository;
import com.excercise.java25ai.repository.EnrollmentRepository;
import com.excercise.java25ai.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiContractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired StudentRepository studentRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired EnrollmentRepository enrollmentRepository;

    @BeforeEach
    void clean() {
        enrollmentRepository.deleteAll();
        studentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private static String studentJson(String email) {
        return """
               {"firstName":"Jaime","lastName":"Medina","email":"%s","dateOfBirth":"1990-05-17"}
               """.formatted(email);
    }

    private static String courseJson(String code, int capacity) {
        return """
               {"code":"%s","title":"Programming","description":"Java","capacity":%d}
               """.formatted(code, capacity);
    }

    private Long createStudent(String email) throws Exception {
        var body = mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON).content(studentJson(email)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    private Long createCourse(String code, int capacity) throws Exception {
        var body = mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON).content(courseJson(code, capacity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return idOf(body);
    }

    private static Long idOf(String json) {
        var marker = "\"id\":";
        var start = json.indexOf(marker) + marker.length();
        var end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    // --- happy paths and status discipline ---

    @Test
    void studentCrudReturnsCorrectStatuses() throws Exception {
        var id = createStudent("a@academy.io");

        mockMvc.perform(get("/api/students/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.fullName").value("Jaime Medina"))
                .andExpect(jsonPath("$.age").isNumber());

        mockMvc.perform(get("/api/students/by-email").param("email", "A@ACADEMY.IO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(put("/api/students/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"firstName":"Jaime","lastName":"Lopez","email":"a@academy.io"}
                                 """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Lopez"));

        // Soft delete is the default and leaves the record in place.
        mockMvc.perform(delete("/api/students/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/students/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(delete("/api/students/{id}", id).param("permanent", "true"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/students/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void pagedListUsesTheStableEnvelope() throws Exception {
        createStudent("a@academy.io");
        createStudent("b@academy.io");

        mockMvc.perform(get("/api/students").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.pageable").doesNotExist());

        mockMvc.perform(get("/api/students").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void courseEndpointsCoverCodeLookupOpenListAndArchive() throws Exception {
        var id = createCourse("JAVA-25", 10);

        mockMvc.perform(get("/api/courses/code/{code}", "java-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.availableSeats").value(10));

        mockMvc.perform(get("/api/courses/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/courses/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/courses/{id}", id))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void enrollmentLifecycleEndpointsWork() throws Exception {
        var studentId = createStudent("a@academy.io");
        var courseId = createCourse("JAVA-25", 10);

        var body = mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"studentId":%d,"courseId":%d,"classroom":"A"}
                                 """.formatted(studentId, courseId)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andExpect(jsonPath("$.courseCode").value("JAVA-25"))
                .andExpect(jsonPath("$.studentName").value("Jaime Medina"))
                .andReturn().getResponse().getContentAsString();
        var enrollmentId = idOf(body);

        mockMvc.perform(get("/api/students/{id}/enrollments", studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/courses/{id}/enrollments", courseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(patch("/api/enrollments/{id}/drop", enrollmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DROPPED"));
        mockMvc.perform(patch("/api/enrollments/{id}/complete", enrollmentId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportingEndpointsReturnJsonAndPlainText() throws Exception {
        var studentId = createStudent("a@academy.io");
        var courseId = createCourse("JAVA-25", 10);
        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"studentId":%d,"courseId":%d,"classroom":"A"}
                                 """.formatted(studentId, courseId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reports/students/by-age-desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/reports/courses/enrolled-students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Programming[0]").value("Jaime Medina"));

        mockMvc.perform(get("/api/reports/courses/enrolled-students/text"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("""
                                            Programming
                                              - Jaime Medina"""));
    }

    // --- error contract ---

    @Test
    void validationFailureReturns400WithFieldErrorMap() throws Exception {
        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"firstName":"","lastName":"Medina","email":"nope","dateOfBirth":"3000-01-01"}
                                 """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/students"))
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").exists());
    }

    @Test
    void notFoundReturns404WithUniformPayloadAndNoFieldErrors() throws Exception {
        mockMvc.perform(get("/api/students/{id}", 9999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Student not found")))
                .andExpect(jsonPath("$.path").value("/api/students/9999"))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void duplicateAndConflictConditionsReturn409() throws Exception {
        createStudent("a@academy.io");
        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON).content(studentJson("A@ACADEMY.IO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));

        var studentId = createStudent("b@academy.io");
        var courseId = createCourse("JAVA-25", 1);
        var enroll = """
                     {"studentId":%d,"courseId":%d,"classroom":"A"}
                     """.formatted(studentId, courseId);
        mockMvc.perform(post("/api/enrollments").contentType(MediaType.APPLICATION_JSON).content(enroll))
                .andExpect(status().isCreated());

        // Same student, same course -> already enrolled, even though the course is now full.
        // The duplicate check deliberately precedes the capacity check.
        mockMvc.perform(post("/api/enrollments").contentType(MediaType.APPLICATION_JSON).content(enroll))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("already actively enrolled")));

        // Different student, capacity of 1 already taken -> course full.
        var other = createStudent("c@academy.io");
        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"studentId":%d,"courseId":%d,"classroom":"B"}
                                 """.formatted(other, courseId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("capacity")));
    }

    @Test
    void businessRuleViolationReturns400() throws Exception {
        var studentId = createStudent("a@academy.io");
        var courseId = createCourse("JAVA-25", 10);
        mockMvc.perform(post("/api/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"studentId":%d,"courseId":%d,"classroom":"A"}
                                 """.formatted(studentId, courseId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/students/{id}", studentId).param("permanent", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("active enrollments")));

        mockMvc.perform(delete("/api/courses/{id}", courseId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedBodyAndBadParameterTypesReturn400() throws Exception {
        mockMvc.perform(post("/api/students").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));

        mockMvc.perform(get("/api/students").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("status")));

        mockMvc.perform(get("/api/students/{id}", "abc"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/students/by-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")));
    }
}
