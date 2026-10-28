package com.excercise.java25ai.entity;

import com.excercise.java25ai.entity.enums.StudentStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent representation of a student enrolled in the academy.
 */
@Entity
@Table(
        name = "students",
        uniqueConstraints = @UniqueConstraint(name = "uk_students_email", columnNames = "email"),
        indexes = @Index(name = "idx_students_status", columnList = "status")
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class StudentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    @ToString.Include
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    @ToString.Include
    private String lastName;

    @Column(name = "email", nullable = false, length = 100)
    @ToString.Include
    private String email;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ToString.Include
    @Builder.Default
    private StudentStatus status = StudentStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(
            mappedBy = "student",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<EnrollmentEntity> enrollments = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = StudentStatus.ACTIVE;
        }
    }

    /**
     * Age in completed years, derived from {@code dateOfBirth} so it can never drift out of date.
     */
    @Transient
    public int getAge() {
        return dateOfBirth == null ? 0 : Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    @Transient
    public String getFullName() {
        return firstName + " " + lastName;
    }

    public void addEnrollment(EnrollmentEntity enrollment) {
        enrollments.add(enrollment);
        enrollment.setStudent(this);
    }

    public void removeEnrollment(EnrollmentEntity enrollment) {
        enrollments.remove(enrollment);
        enrollment.setStudent(null);
    }
}
