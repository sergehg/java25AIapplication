package com.excercise.java25ai.service.impl;

import com.excercise.java25ai.dto.CreateStudentRequest;
import com.excercise.java25ai.dto.StudentResponse;
import com.excercise.java25ai.dto.UpdateStudentRequest;
import com.excercise.java25ai.entity.StudentEntity;
import com.excercise.java25ai.entity.enums.EnrollmentStatus;
import com.excercise.java25ai.entity.enums.StudentStatus;
import com.excercise.java25ai.exception.BusinessRuleViolationException;
import com.excercise.java25ai.exception.DuplicateResourceException;
import com.excercise.java25ai.exception.ResourceNotFoundException;
import com.excercise.java25ai.mapper.StudentMapper;
import com.excercise.java25ai.repository.EnrollmentRepository;
import com.excercise.java25ai.repository.StudentRepository;
import com.excercise.java25ai.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentServiceImpl implements StudentService {

    private static final String RESOURCE_NAME = "Student";

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentMapper studentMapper;

    @Override
    @Transactional
    public StudentResponse create(CreateStudentRequest request) {
        if (studentRepository.existsByEmailIgnoreCase(request.email())) {
            throw DuplicateResourceException.of(RESOURCE_NAME, "email", request.email());
        }
        var saved = studentRepository.save(studentMapper.toEntity(request));
        return studentMapper.toResponse(saved);
    }

    @Override
    public StudentResponse getById(Long id) {
        return studentMapper.toResponse(findEntityById(id));
    }

    @Override
    public StudentResponse getByEmail(String email) {
        return studentRepository.findByEmailIgnoreCase(email)
                .map(studentMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, email));
    }

    @Override
    public Page<StudentResponse> findAll(StudentStatus status, Pageable pageable) {
        var page = status == null
                ? studentRepository.findAll(pageable)
                : studentRepository.findByStatus(status, pageable);
        return page.map(studentMapper::toResponse);
    }

    @Override
    @Transactional
    public StudentResponse update(Long id, UpdateStudentRequest request) {
        var student = findEntityById(id);
        if (studentRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw DuplicateResourceException.of(RESOURCE_NAME, "email", request.email());
        }
        student.setFirstName(request.firstName());
        student.setLastName(request.lastName());
        student.setEmail(request.email());
        return studentMapper.toResponse(student);
    }

    @Override
    @Transactional
    public StudentResponse deactivate(Long id) {
        var student = findEntityById(id);
        student.setStatus(StudentStatus.INACTIVE);
        return studentMapper.toResponse(student);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        var student = findEntityById(id);
        if (enrollmentRepository.existsByStudentIdAndStatus(id, EnrollmentStatus.ENROLLED)) {
            throw new BusinessRuleViolationException(
                    "Student %d still has active enrollments; drop or complete them first, or deactivate the student"
                            .formatted(id));
        }
        if (enrollmentRepository.existsByStudentId(id)) {
            throw new BusinessRuleViolationException(
                    "Student %d has enrollment history that must be retained; deactivate the student instead"
                            .formatted(id));
        }
        studentRepository.delete(student);
    }

    private StudentEntity findEntityById(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
    }
}
