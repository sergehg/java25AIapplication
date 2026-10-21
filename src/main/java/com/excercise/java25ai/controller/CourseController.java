package com.excercise.java25ai.controller;

import com.excercise.java25ai.dto.CourseResponse;
import com.excercise.java25ai.dto.CreateCourseRequest;
import com.excercise.java25ai.dto.PagedResponse;
import com.excercise.java25ai.dto.UpdateCourseRequest;
import com.excercise.java25ai.entity.enums.CourseStatus;
import com.excercise.java25ai.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourseRequest request) {
        var created = courseService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public CourseResponse getById(@PathVariable Long id) {
        return courseService.getById(id);
    }

    /**
     * Course codes resolve case-insensitively, so {@code JAVA-25} and {@code java-25} both work.
     */
    @GetMapping("/code/{code}")
    public CourseResponse getByCode(@PathVariable String code) {
        return courseService.getByCode(code);
    }

    /**
     * @param status optional filter; omit it to list courses of every status
     */
    @GetMapping
    public PagedResponse<CourseResponse> findAll(
            @RequestParam(required = false) CourseStatus status,
            @PageableDefault(size = 20, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return PagedResponse.from(courseService.findAll(status, pageable));
    }

    /**
     * Every course currently accepting enrollments.
     */
    @GetMapping("/open")
    public List<CourseResponse> findOpenCourses() {
        return courseService.findOpenCourses();
    }

    /**
     * Updates title, description and capacity. Capacity may not be reduced below the number of
     * students currently enrolled.
     */
    @PutMapping("/{id}")
    public CourseResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCourseRequest request) {
        return courseService.update(id, request);
    }

    /**
     * Soft-deletes the course by marking it {@code ARCHIVED}. Pass {@code permanent=true} to
     * attempt a hard delete instead. Either way the operation is rejected while active
     * enrollments remain, and a hard delete is also rejected if historical enrollments exist.
     *
     * @return 204 in both cases
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(defaultValue = "false") boolean permanent) {
        if (permanent) {
            courseService.delete(id);
        } else {
            courseService.archive(id);
        }
        return ResponseEntity.noContent().build();
    }
}
