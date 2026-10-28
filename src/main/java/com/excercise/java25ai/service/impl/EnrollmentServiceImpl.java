package com.excercise.java25ai.service.impl;

import com.excercise.java25ai.dto.EnrollStudentRequest;
import com.excercise.java25ai.dto.EnrollmentResponse;
import com.excercise.java25ai.entity.CourseEntity;
import com.excercise.java25ai.entity.EnrollmentEntity;
import com.excercise.java25ai.entity.StudentEntity;
import com.excercise.java25ai.entity.enums.CourseStatus;
import com.excercise.java25ai.entity.enums.EnrollmentStatus;
import com.excercise.java25ai.entity.enums.StudentStatus;
import com.excercise.java25ai.exception.AlreadyEnrolledException;
import com.excercise.java25ai.exception.BusinessRuleViolationException;
import com.excercise.java25ai.exception.CourseFullException;
import com.excercise.java25ai.exception.ResourceNotFoundException;
import com.excercise.java25ai.mapper.EnrollmentMapper;
import com.excercise.java25ai.repository.CourseRepository;
import com.excercise.java25ai.repository.EnrollmentRepository;
import com.excercise.java25ai.repository.StudentRepository;
import com.excercise.java25ai.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentServiceImpl implements EnrollmentService {

    private static final String RESOURCE_NAME = "Enrollment";

    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentMapper enrollmentMapper;

    @Override
    @Transactional
    public EnrollmentResponse enroll(EnrollStudentRequest request) {
        var student = findStudent(request.studentId());
        if (student.getStatus() != StudentStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "Only ACTIVE students may enroll; student %d is %s"
                            .formatted(student.getId(), student.getStatus()));
        }

        // Pessimistic lock: serialises concurrent enrollments so two requests cannot both pass
        // the capacity check below and overfill the course.
        var course = courseRepository.findByIdForUpdate(request.courseId())
                .orElseThrow(() -> ResourceNotFoundException.of("Course", request.courseId()));

        // Archival wins over everything else: it is a deliberate administrative decision rather
        // than a consequence of occupancy.
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new BusinessRuleViolationException(
                    "Course '%s' is archived and no longer accepts enrollments".formatted(course.getCode()));
        }

        // Any prior enrollment is resolved before the capacity check. A student who already holds
        // a seat must be told they are already enrolled, not that the course is full — the latter
        // is technically true yet misleading, since one of those seats is theirs.
        var priorEnrollment = enrollmentRepository.findByStudentIdAndCourseId(student.getId(), course.getId());
        priorEnrollment.ifPresent(existing -> rejectIfNotReEnrollable(existing, student, course));

        // Only reached when this student is not currently occupying a seat, so a full course is
        // genuinely full for them. The status check comes last: a course that fills up closes
        // automatically, so testing the status first would mask every full course behind a vague
        // "not open" message and leave CourseFullException unreachable.
        long activeEnrollments = countActive(course.getId());
        if (activeEnrollments >= course.getCapacity()) {
            throw CourseFullException.of(course.getCode(), course.getCapacity());
        }

        if (course.getStatus() != CourseStatus.OPEN) {
            throw new BusinessRuleViolationException(
                    "Course '%s' is not open for enrollment; current status is %s"
                            .formatted(course.getCode(), course.getStatus()));
        }

        var enrollment = priorEnrollment
                .map(existing -> reactivate(existing, request))
                .orElseGet(() -> EnrollmentEntity.builder()
                        .student(student)
                        .course(course)
                        .classroom(request.classroom())
                        .status(EnrollmentStatus.ENROLLED)
                        .build());

        var saved = enrollmentRepository.saveAndFlush(enrollment);
        course.applyCapacityStatus(activeEnrollments + 1);

        return enrollmentMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public EnrollmentResponse drop(Long enrollmentId) {
        return closeOut(enrollmentId, EnrollmentStatus.DROPPED);
    }

    @Override
    @Transactional
    public EnrollmentResponse complete(Long enrollmentId) {
        return closeOut(enrollmentId, EnrollmentStatus.COMPLETED);
    }

    @Override
    public EnrollmentResponse getById(Long enrollmentId) {
        return enrollmentMapper.toResponse(findEnrollment(enrollmentId));
    }

    @Override
    public Page<EnrollmentResponse> findByStudent(Long studentId, Pageable pageable) {
        if (!studentRepository.existsById(studentId)) {
            throw ResourceNotFoundException.of("Student", studentId);
        }
        return enrollmentRepository.findByStudentId(studentId, pageable).map(enrollmentMapper::toResponse);
    }

    @Override
    public Page<EnrollmentResponse> findByCourse(Long courseId, Pageable pageable) {
        if (!courseRepository.existsById(courseId)) {
            throw ResourceNotFoundException.of("Course", courseId);
        }
        return enrollmentRepository.findByCourseId(courseId, pageable).map(enrollmentMapper::toResponse);
    }

    /**
     * Rejects a prior enrollment that cannot be re-enrolled. Only a {@code DROPPED} record is
     * eligible; an active one is a duplicate, and a completed one must not be overwritten.
     */
    private void rejectIfNotReEnrollable(EnrollmentEntity existing, StudentEntity student, CourseEntity course) {
        switch (existing.getStatus()) {
            case ENROLLED -> throw AlreadyEnrolledException.of(student.getId(), course.getCode());
            case COMPLETED -> throw new BusinessRuleViolationException(
                    "Student %d has already completed course '%s' and cannot re-enroll"
                            .formatted(student.getId(), course.getCode()));
            case DROPPED -> {
                // Eligible for re-enrollment.
            }
        }
    }

    /**
     * Re-enrollment policy: a previously {@code DROPPED} record is flipped back to
     * {@code ENROLLED} in place rather than inserting a second row for the same pair.
     */
    private EnrollmentEntity reactivate(EnrollmentEntity existing, EnrollStudentRequest request) {
        existing.setStatus(EnrollmentStatus.ENROLLED);
        existing.setClassroom(request.classroom());
        existing.setEnrolledAt(LocalDateTime.now());
        return existing;
    }

    /**
     * Moves an active enrollment to a terminal status and hands the freed seat back to the
     * course, reopening it if it had closed.
     */
    private EnrollmentResponse closeOut(Long enrollmentId, EnrollmentStatus terminalStatus) {
        var enrollment = findEnrollment(enrollmentId);
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED) {
            throw new BusinessRuleViolationException(
                    "Enrollment %d is %s; only an ENROLLED record can be marked %s"
                            .formatted(enrollmentId, enrollment.getStatus(), terminalStatus));
        }

        var courseId = enrollment.getCourse().getId();
        var course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> ResourceNotFoundException.of("Course", courseId));

        enrollment.setStatus(terminalStatus);
        enrollmentRepository.saveAndFlush(enrollment);

        course.applyCapacityStatus(countActive(courseId));

        return enrollmentMapper.toResponse(enrollment);
    }

    private long countActive(Long courseId) {
        return enrollmentRepository.countByCourseIdAndStatus(courseId, EnrollmentStatus.ENROLLED);
    }

    private EnrollmentEntity findEnrollment(Long enrollmentId) {
        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, enrollmentId));
    }

    private StudentEntity findStudent(Long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Student", studentId));
    }
}
