package com.excercise.java25ai.controller;

import com.excercise.java25ai.dto.StudentResponse;
import com.excercise.java25ai.service.ReportingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Read-only reporting endpoints backed by the Streams API implementations in the service layer.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportingController {

    private final ReportingService reportingService;

    /**
     * All students ordered from oldest to youngest.
     */
    @GetMapping("/students/by-age-desc")
    public List<StudentResponse> getStudentsSortedByAgeDesc() {
        return reportingService.getStudentsSortedByAgeDesc();
    }

    /**
     * Actively enrolled students grouped under their course title, both ordered alphabetically.
     */
    @GetMapping("/courses/enrolled-students")
    public Map<String, List<String>> getEnrolledStudentsByCourse() {
        return reportingService.getEnrolledStudentsByCourse();
    }

    /**
     * The same grouping rendered as the plain-text report described in the business rules.
     */
    @GetMapping(value = "/courses/enrolled-students/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getEnrolledStudentsByCourseReport() {
        return reportingService.getEnrolledStudentsByCourseReport();
    }
}
