package com.excercise.java25ai.repository;

import com.excercise.java25ai.entity.StudentEntity;
import com.excercise.java25ai.entity.enums.StudentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link StudentEntity}.
 *
 * <p>Every lookup is a derived query or a parameterised JPQL query; no statement is ever built by
 * string concatenation, which keeps SQL injection out of this layer entirely.
 */
@Repository
public interface StudentRepository extends JpaRepository<StudentEntity, Long> {

    Optional<StudentEntity> findByEmailIgnoreCase(String email);

    /**
     * Email uniqueness check for the creation flow.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Email uniqueness check for the update flow, excluding the student being updated.
     */
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    Page<StudentEntity> findByStatus(StudentStatus status, Pageable pageable);

    List<StudentEntity> findByStatus(StudentStatus status);

    /**
     * Loads students together with their enrollments and courses in a single round trip,
     * for the course-to-student report.
     */
    @EntityGraph(attributePaths = {"enrollments", "enrollments.course"})
    List<StudentEntity> findAllWithEnrollmentsBy();
}
