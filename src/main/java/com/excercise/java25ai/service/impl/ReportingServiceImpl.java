package com.excercise.java25ai.service.impl;

import com.excercise.java25ai.dto.StudentResponse;
import com.excercise.java25ai.entity.StudentEntity;
import com.excercise.java25ai.entity.enums.EnrollmentStatus;
import com.excercise.java25ai.mapper.StudentMapper;
import com.excercise.java25ai.repository.EnrollmentRepository;
import com.excercise.java25ai.repository.StudentRepository;
import com.excercise.java25ai.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Analytical views built entirely with the Streams API, as required by the functional
 * programming section of the business rules.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportingServiceImpl implements ReportingService {

    /**
     * Oldest first. Last and first name break ties so the ordering is deterministic for
     * students who share an age.
     */
    private static final Comparator<StudentEntity> BY_AGE_DESCENDING =
            Comparator.comparingInt(StudentEntity::getAge).reversed()
                    .thenComparing(StudentEntity::getLastName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(StudentEntity::getFirstName, String.CASE_INSENSITIVE_ORDER);

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentMapper studentMapper;

    @Override
    public List<StudentResponse> getStudentsSortedByAgeDesc() {
        return studentRepository.findAll().stream()
                .sorted(BY_AGE_DESCENDING)
                .map(studentMapper::toResponse)
                .toList();
    }

    @Override
    public Map<String, List<String>> getEnrolledStudentsByCourse() {
        return enrollmentRepository.findAllByStatusWithStudentAndCourse(EnrollmentStatus.ENROLLED).stream()
                .collect(Collectors.groupingBy(
                        enrollment -> enrollment.getCourse().getTitle(),
                        TreeMap::new,
                        Collectors.mapping(
                                enrollment -> enrollment.getStudent().getFullName(),
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        names -> names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()))));
    }

    @Override
    public String getEnrolledStudentsByCourseReport() {
        return getEnrolledStudentsByCourse().entrySet().stream()
                .map(entry -> entry.getValue().stream()
                        .map("  - "::concat)
                        .collect(Collectors.joining("\n", entry.getKey() + "\n", "")))
                .collect(Collectors.joining("\n"));
    }
}
