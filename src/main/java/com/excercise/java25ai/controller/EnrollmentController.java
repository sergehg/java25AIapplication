package com.excercise.java25ai.controller;

import com.excercise.java25ai.dto.EnrollStudentRequest;
import com.excercise.java25ai.dto.EnrollmentResponse;
import com.excercise.java25ai.dto.PagedResponse;
import com.excercise.java25ai.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Enrollment endpoints.
 *
 * <p>No class-level base path: the collection lives at {@code /api/enrollments}, while the two
 * read endpoints are nested under the resource that owns them.
 */
@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    /**
     * Enrolls an active student in an open course. A previously dropped enrollment is reactivated
     * in place rather than duplicated.
     *
     * @return 201 with the enrollment and a {@code Location} header
     */
    @PostMapping("/api/enrollments")
    public ResponseEntity<EnrollmentResponse> enroll(@Valid @RequestBody EnrollStudentRequest request) {
        var enrollment = enrollmentService.enroll(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(enrollment.id())
                .toUri();
        return ResponseEntity.created(location).body(enrollment);
    }

    @GetMapping("/api/enrollments/{id}")
    public EnrollmentResponse getById(@PathVariable Long id) {
        return enrollmentService.getById(id);
    }

    /**
     * Drops an active enrollment, freeing a seat and reopening the course if it had closed.
     */
    @PatchMapping("/api/enrollments/{id}/drop")
    public EnrollmentResponse drop(@PathVariable Long id) {
        return enrollmentService.drop(id);
    }

    /**
     * Marks an active enrollment completed, freeing a seat and reopening the course if it had
     * closed.
     */
    @PatchMapping("/api/enrollments/{id}/complete")
    public EnrollmentResponse complete(@PathVariable Long id) {
        return enrollmentService.complete(id);
    }

    /**
     * Full enrollment history for one student, active and historical.
     */
    @GetMapping("/api/students/{studentId}/enrollments")
    public PagedResponse<EnrollmentResponse> findByStudent(
            @PathVariable Long studentId,
            @PageableDefault(size = 20, sort = "enrolledAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PagedResponse.from(enrollmentService.findByStudent(studentId, pageable));
    }

    /**
     * Full enrollment history for one course, active and historical.
     */
    @GetMapping("/api/courses/{courseId}/enrollments")
    public PagedResponse<EnrollmentResponse> findByCourse(
            @PathVariable Long courseId,
            @PageableDefault(size = 20, sort = "enrolledAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PagedResponse.from(enrollmentService.findByCourse(courseId, pageable));
    }
}
