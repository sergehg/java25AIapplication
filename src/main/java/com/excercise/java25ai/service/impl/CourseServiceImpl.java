package com.excercise.java25ai.service.impl;

import com.excercise.java25ai.dto.CourseResponse;
import com.excercise.java25ai.dto.CreateCourseRequest;
import com.excercise.java25ai.dto.UpdateCourseRequest;
import com.excercise.java25ai.entity.CourseEntity;
import com.excercise.java25ai.entity.enums.CourseStatus;
import com.excercise.java25ai.entity.enums.EnrollmentStatus;
import com.excercise.java25ai.exception.BusinessRuleViolationException;
import com.excercise.java25ai.exception.DuplicateResourceException;
import com.excercise.java25ai.exception.ResourceNotFoundException;
import com.excercise.java25ai.mapper.CourseMapper;
import com.excercise.java25ai.repository.CourseRepository;
import com.excercise.java25ai.repository.EnrollmentRepository;
import com.excercise.java25ai.repository.projection.CourseEnrollmentCount;
import com.excercise.java25ai.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseServiceImpl implements CourseService {

    private static final String RESOURCE_NAME = "Course";

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseMapper courseMapper;

    @Override
    @Transactional
    public CourseResponse create(CreateCourseRequest request) {
        if (courseRepository.existsByCodeIgnoreCase(request.code())) {
            throw DuplicateResourceException.of(RESOURCE_NAME, "code", request.code());
        }
        var saved = courseRepository.save(courseMapper.toEntity(request));
        return courseMapper.toResponse(saved, 0L);
    }

    @Override
    public CourseResponse getById(Long id) {
        return toResponse(findEntityById(id));
    }

    @Override
    public CourseResponse getByCode(String code) {
        return courseRepository.findByCodeIgnoreCase(code)
                .map(this::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, code));
    }

    @Override
    public Page<CourseResponse> findAll(CourseStatus status, Pageable pageable) {
        var page = status == null
                ? courseRepository.findAll(pageable)
                : courseRepository.findByStatus(status, pageable);
        var occupancy = countActiveEnrollments(page.getContent());
        return page.map(course -> courseMapper.toResponse(course, occupancy.getOrDefault(course.getId(), 0L)));
    }

    @Override
    public List<CourseResponse> findOpenCourses() {
        var courses = courseRepository.findByStatus(CourseStatus.OPEN);
        var occupancy = countActiveEnrollments(courses);
        return courses.stream()
                .map(course -> courseMapper.toResponse(course, occupancy.getOrDefault(course.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public CourseResponse update(Long id, UpdateCourseRequest request) {
        var course = findEntityById(id);
        long activeEnrollments = countActiveEnrollments(id);

        if (request.capacity() < activeEnrollments) {
            throw new BusinessRuleViolationException(
                    "Cannot reduce capacity of course '%s' to %d: %d students are currently enrolled"
                            .formatted(course.getCode(), request.capacity(), activeEnrollments));
        }

        course.setTitle(request.title());
        course.setDescription(request.description());
        course.setCapacity(request.capacity());
        course.applyCapacityStatus(activeEnrollments);

        return courseMapper.toResponse(course, activeEnrollments);
    }

    @Override
    @Transactional
    public CourseResponse archive(Long id) {
        var course = findEntityById(id);
        if (enrollmentRepository.existsByCourseIdAndStatus(id, EnrollmentStatus.ENROLLED)) {
            throw new BusinessRuleViolationException(
                    "Course '%s' still has active enrollments and cannot be archived"
                            .formatted(course.getCode()));
        }
        course.setStatus(CourseStatus.ARCHIVED);
        return toResponse(course);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        var course = findEntityById(id);
        if (enrollmentRepository.existsByCourseIdAndStatus(id, EnrollmentStatus.ENROLLED)) {
            throw new BusinessRuleViolationException(
                    "Course '%s' still has active enrollments and cannot be deleted"
                            .formatted(course.getCode()));
        }
        if (enrollmentRepository.existsByCourseId(id)) {
            throw new BusinessRuleViolationException(
                    "Course '%s' has enrollment history that must be retained; archive it instead"
                            .formatted(course.getCode()));
        }
        courseRepository.delete(course);
    }

    private CourseEntity findEntityById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE_NAME, id));
    }

    private CourseResponse toResponse(CourseEntity course) {
        return courseMapper.toResponse(course, countActiveEnrollments(course.getId()));
    }

    private long countActiveEnrollments(Long courseId) {
        return enrollmentRepository.countByCourseIdAndStatus(courseId, EnrollmentStatus.ENROLLED);
    }

    /**
     * Resolves occupancy for a whole collection of courses in a single query. Courses with no
     * active enrollments are simply absent from the result and default to zero at the call site.
     */
    private Map<Long, Long> countActiveEnrollments(List<CourseEntity> courses) {
        if (courses.isEmpty()) {
            return Map.of();
        }
        var courseIds = courses.stream().map(CourseEntity::getId).toList();
        return enrollmentRepository.countByCourseIdInAndStatus(courseIds, EnrollmentStatus.ENROLLED).stream()
                .collect(Collectors.toMap(CourseEnrollmentCount::courseId, CourseEnrollmentCount::total));
    }
}
