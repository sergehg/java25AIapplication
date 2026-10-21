package com.excercise.java25ai.entity;

import com.excercise.java25ai.entity.enums.Classroom;
import com.excercise.java25ai.entity.enums.EnrollmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

import java.time.LocalDateTime;

/**
 * Join entity linking a {@link StudentEntity} to a {@link CourseEntity}, carrying the
 * enrollment payload (status, classroom, timestamp).
 *
 * <p>The composite unique constraint on {@code (student_id, course_id)} is what makes the
 * re-enrollment policy safe: a dropped enrollment is flipped back to {@code ENROLLED} in place
 * instead of inserting a second row, and the database rejects duplicates even under a race.
 */
@Entity
@Table(
        name = "enrollments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_enrollments_student_course",
                columnNames = {"student_id", "course_id"}
        ),
        indexes = {
                @Index(name = "idx_enrollments_course_status", columnList = "course_id, status"),
                @Index(name = "idx_enrollments_student_status", columnList = "student_id, status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class EnrollmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "student_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_enrollments_student")
    )
    private StudentEntity student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "course_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_enrollments_course")
    )
    private CourseEntity course;

    @Column(name = "enrolled_at", nullable = false)
    private LocalDateTime enrolledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ToString.Include
    @Builder.Default
    private EnrollmentStatus status = EnrollmentStatus.ENROLLED;

    @Enumerated(EnumType.STRING)
    @Column(name = "classroom", nullable = false, length = 1)
    @ToString.Include
    private Classroom classroom;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    void onCreate() {
        if (enrolledAt == null) {
            enrolledAt = LocalDateTime.now();
        }
        if (status == null) {
            status = EnrollmentStatus.ENROLLED;
        }
    }
}
