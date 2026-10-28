package com.excercise.java25ai.controller;

import com.excercise.java25ai.dto.CreateStudentRequest;
import com.excercise.java25ai.dto.PagedResponse;
import com.excercise.java25ai.dto.StudentResponse;
import com.excercise.java25ai.dto.UpdateStudentRequest;
import com.excercise.java25ai.entity.enums.StudentStatus;
import com.excercise.java25ai.service.StudentService;
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

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /**
     * @return 201 with the created student and a {@code Location} header
     */
    @PostMapping
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody CreateStudentRequest request) {
        var created = studentService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public StudentResponse getById(@PathVariable Long id) {
        return studentService.getById(id);
    }

    @GetMapping("/by-email")
    public StudentResponse getByEmail(@RequestParam String email) {
        return studentService.getByEmail(email);
    }

    /**
     * @param status optional filter; omit it to list students of every status
     */
    @GetMapping
    public PagedResponse<StudentResponse> findAll(
            @RequestParam(required = false) StudentStatus status,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return PagedResponse.from(studentService.findAll(status, pageable));
    }

    @PutMapping("/{id}")
    public StudentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateStudentRequest request) {
        return studentService.update(id, request);
    }

    /**
     * Soft-deletes the student by marking them {@code INACTIVE}, which is the preferred removal
     * path. Pass {@code permanent=true} to attempt a hard delete instead; that is rejected while
     * the student has any enrollment record.
     *
     * @return 204 in both cases
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestParam(defaultValue = "false") boolean permanent) {
        if (permanent) {
            studentService.delete(id);
        } else {
            studentService.deactivate(id);
        }
        return ResponseEntity.noContent().build();
    }
}
