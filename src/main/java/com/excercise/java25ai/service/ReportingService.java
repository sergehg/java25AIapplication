package com.excercise.java25ai.service;

import com.excercise.java25ai.dto.StudentResponse;

import java.util.List;
import java.util.Map;

/**
 * Read-only analytical views over the academy, implemented with the Streams API.
 */
public interface ReportingService {

    /**
     * All students ordered from oldest to youngest.
     */
    List<StudentResponse> getStudentsSortedByAgeDesc();

    /**
     * Actively enrolled students grouped under their course title.
     *
     * @return course title to the names of its enrolled students, both ordered alphabetically
     */
    Map<String, List<String>> getEnrolledStudentsByCourse();

    /**
     * The same grouping rendered as the plain-text report described in the business rules:
     * <pre>
     * Programming
     *   - Jaime Medina
     * Database
     *   - Mito X
     * </pre>
     */
    String getEnrolledStudentsByCourseReport();
}
