package com.excercise.java25ai.entity;

import com.excercise.java25ai.entity.enums.CourseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent representation of a course offered by the academy.
 */
@Entity
@Table(
        name = "courses",
        uniqueConstraints = @UniqueConstraint(name = "uk_courses_code", columnNames = "code"),
        indexes = @Index(name = "idx_courses_status", columnList = "status")
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class CourseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(name = "code", nullable = false, length = 20)
    @ToString.Include
    private String code;

    @Column(name = "title", nullable = false, length = 100)
    @ToString.Include
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "capacity", nullable = false)
    @ToString.Include
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ToString.Include
    @Builder.Default
    private CourseStatus status = CourseStatus.OPEN;

    /**
     * Optimistic lock guard for the capacity and status transitions driven by the enrollment flow.
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * No cascade on purpose: archiving or removing a course must never wipe enrollment history.
     */
    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY)
    @Builder.Default
    private List<EnrollmentEntity> enrollments = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = CourseStatus.OPEN;
        }
    }

    /**
     * Re-evaluates availability against the current active enrollment count: a course that has
     * reached capacity becomes {@code CLOSED}, and one that has a seat free again becomes
     * {@code OPEN}. An {@code ARCHIVED} course is left alone — archival is an administrative
     * decision that occupancy must not undo.
     *
     * @param activeEnrollments current count of {@code ENROLLED} records for this course
     */
    public void applyCapacityStatus(long activeEnrollments) {
        if (status == CourseStatus.ARCHIVED) {
            return;
        }
        status = activeEnrollments >= capacity ? CourseStatus.CLOSED : CourseStatus.OPEN;
    }

    public void addEnrollment(EnrollmentEntity enrollment) {
        enrollments.add(enrollment);
        enrollment.setCourse(this);
    }

    public void removeEnrollment(EnrollmentEntity enrollment) {
        enrollments.remove(enrollment);
        enrollment.setCourse(null);
    }
}
