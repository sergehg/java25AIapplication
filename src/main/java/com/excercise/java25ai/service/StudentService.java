package com.excercise.java25ai.service;

import com.excercise.java25ai.dto.CreateStudentRequest;
import com.excercise.java25ai.dto.StudentResponse;
import com.excercise.java25ai.dto.UpdateStudentRequest;
import com.excercise.java25ai.entity.enums.StudentStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Student lifecycle and CRUD operations.
 *
 * <p>Validation constraints are declared here, on the contract, and never repeated on the
 * implementation — Bean Validation forbids strengthening parameter constraints in a subtype.
 */
public interface StudentService {

    StudentResponse create(@Valid CreateStudentRequest request);

    StudentResponse getById(Long id);

    StudentResponse getByEmail(String email);

    /**
     * @param status optional filter; {@code null} returns students of every status
     */
    Page<StudentResponse> findAll(StudentStatus status, Pageable pageable);

    StudentResponse update(Long id, @Valid UpdateStudentRequest request);

    /**
     * Soft delete: marks the student {@code INACTIVE}, which also bars them from enrolling in
     * new courses. This is the preferred removal path and is always permitted.
     */
    StudentResponse deactivate(Long id);

    /**
     * Hard delete. Rejected when the student has any enrollment record, active or historical,
     * because enrollments must not be erased by a cascade.
     */
    void delete(Long id);
}
