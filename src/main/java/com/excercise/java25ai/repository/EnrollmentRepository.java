package com.excercise.java25ai.repository;

import com.excercise.java25ai.entity.EnrollmentEntity;
import com.excercise.java25ai.entity.enums.EnrollmentStatus;
import com.excercise.java25ai.repository.projection.CourseEnrollmentCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link EnrollmentEntity}.
 */
@Repository
public interface EnrollmentRepository extends JpaRepository<EnrollmentEntity, Long> {

    /**
     * Backs the re-enrollment policy: an existing (possibly DROPPED) record is reused
     * instead of inserting a duplicate row.
     */
    Optional<EnrollmentEntity> findByStudentIdAndCourseId(Long studentId, Long courseId);

    /**
     * Duplicate active enrollment check.
     */
    boolean existsByStudentIdAndCourseIdAndStatus(Long studentId, Long courseId, EnrollmentStatus status);

    /**
     * Active seat count for a course, used for capacity enforcement and the automatic
     * transition of a full course to CLOSED.
     */
    long countByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    /**
     * Blocks student deletion while active enrollments remain.
     */
    boolean existsByStudentIdAndStatus(Long studentId, EnrollmentStatus status);

    /**
     * Blocks course deletion or archival while active enrollments remain.
     */
    boolean existsByCourseIdAndStatus(Long courseId, EnrollmentStatus status);

    /**
     * Any enrollment row at all, active or historical. Guards hard deletes: enrollment records
     * carry historical value and must not be removed by a cascade.
     */
    boolean existsByStudentId(Long studentId);

    boolean existsByCourseId(Long courseId);

    Page<EnrollmentEntity> findByStudentId(Long studentId, Pageable pageable);

    Page<EnrollmentEntity> findByCourseId(Long courseId, Pageable pageable);

    List<EnrollmentEntity> findByStudentIdAndStatus(Long studentId, EnrollmentStatus status);

    /**
     * Tallies active enrollments for many courses at once, so listing a page of courses costs
     * one extra query rather than one per course.
     */
    @Query("""
           select new com.excercise.java25ai.repository.projection.CourseEnrollmentCount(
                  e.course.id, count(e))
           from EnrollmentEntity e
           where e.course.id in :courseIds
             and e.status = :status
           group by e.course.id
           """)
    List<CourseEnrollmentCount> countByCourseIdInAndStatus(@Param("courseIds") Collection<Long> courseIds,
                                                           @Param("status") EnrollmentStatus status);

    /**
     * Loads enrollments with both associations already fetched, so grouping courses to students
     * does not trigger one query per row.
     */
    @Query("""
           select e from EnrollmentEntity e
           join fetch e.student
           join fetch e.course
           where e.status = :status
           """)
    List<EnrollmentEntity> findAllByStatusWithStudentAndCourse(@Param("status") EnrollmentStatus status);
}
