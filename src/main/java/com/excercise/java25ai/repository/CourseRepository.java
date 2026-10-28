package com.excercise.java25ai.repository;

import com.excercise.java25ai.entity.CourseEntity;
import com.excercise.java25ai.entity.enums.CourseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link CourseEntity}.
 */
@Repository
public interface CourseRepository extends JpaRepository<CourseEntity, Long> {

    /**
     * Course codes are unique case-insensitively (e.g. {@code JAVA-25} equals {@code java-25}).
     */
    Optional<CourseEntity> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    Page<CourseEntity> findByStatus(CourseStatus status, Pageable pageable);

    List<CourseEntity> findByStatus(CourseStatus status);

    /**
     * Pessimistically locks the course row so concurrent enrollment attempts into the same course
     * are serialised. Without this, two requests can both pass the capacity check and overfill the
     * course. Must be called inside an active transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CourseEntity c where c.id = :id")
    Optional<CourseEntity> findByIdForUpdate(@Param("id") Long id);
}
