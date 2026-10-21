package com.excercise.java25ai.repository.projection;

/**
 * Active enrollment tally for a single course, used to resolve occupancy for a whole page of
 * courses in one query instead of one count per row.
 */
public record CourseEnrollmentCount(Long courseId, long total) {
}
