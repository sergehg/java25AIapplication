package com.excercise.java25ai.mapper;

import com.excercise.java25ai.dto.CreateStudentRequest;
import com.excercise.java25ai.dto.StudentResponse;
import com.excercise.java25ai.entity.StudentEntity;
import com.excercise.java25ai.entity.enums.StudentStatus;
import org.springframework.stereotype.Component;

/**
 * Translates between {@link StudentEntity} and its DTOs. Stateless, therefore thread-safe.
 */
@Component
public class StudentMapper {

    /**
     * Builds a new entity from a creation payload. The initial status is fixed to
     * {@code ACTIVE} here rather than taken from the caller.
     */
    public StudentEntity toEntity(CreateStudentRequest request) {
        return StudentEntity.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .dateOfBirth(request.dateOfBirth())
                .status(StudentStatus.ACTIVE)
                .build();
    }

    public StudentResponse toResponse(StudentEntity student) {
        return new StudentResponse(
                student.getId(),
                student.getFirstName(),
                student.getLastName(),
                student.getFullName(),
                student.getEmail(),
                student.getDateOfBirth(),
                student.getAge(),
                student.getStatus(),
                student.getCreatedAt()
        );
    }
}
