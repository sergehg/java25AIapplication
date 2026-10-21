package com.excercise.java25ai.service.impl;

import com.excercise.java25ai.dto.CreateStudentRequest;
import com.excercise.java25ai.dto.UpdateStudentRequest;
import com.excercise.java25ai.entity.StudentEntity;
import com.excercise.java25ai.entity.enums.EnrollmentStatus;
import com.excercise.java25ai.entity.enums.StudentStatus;
import com.excercise.java25ai.exception.BusinessRuleViolationException;
import com.excercise.java25ai.exception.DuplicateResourceException;
import com.excercise.java25ai.exception.ResourceNotFoundException;
import com.excercise.java25ai.mapper.StudentMapper;
import com.excercise.java25ai.repository.EnrollmentRepository;
import com.excercise.java25ai.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StudentServiceImpl}. Repositories are mocked; the mapper is the real
 * stateless implementation so the assertions cover the mapping too.
 */
@ExtendWith(MockitoExtension.class)
class StudentServiceImplTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 17);

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;

    private StudentServiceImpl studentService;

    @BeforeEach
    void setUp() {
        studentService = new StudentServiceImpl(studentRepository, enrollmentRepository, new StudentMapper());
    }

    private static StudentEntity student(Long id, String email, StudentStatus status) {
        return StudentEntity.builder()
                .id(id)
                .firstName("Jaime")
                .lastName("Medina")
                .email(email)
                .dateOfBirth(BIRTH_DATE)
                .status(status)
                .createdAt(LocalDateTime.of(2026, 1, 1, 9, 0))
                .build();
    }

    private static CreateStudentRequest createRequest(String email) {
        return new CreateStudentRequest("Jaime", "Medina", email, BIRTH_DATE);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void savesTheStudentAsActive() {
            when(studentRepository.existsByEmailIgnoreCase("a@academy.io")).thenReturn(false);
            when(studentRepository.save(any(StudentEntity.class)))
                    .thenAnswer(invocation -> {
                        StudentEntity toSave = invocation.getArgument(0);
                        toSave.setId(1L);
                        toSave.setCreatedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
                        return toSave;
                    });

            var response = studentService.create(createRequest("a@academy.io"));

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.status()).isEqualTo(StudentStatus.ACTIVE);
            assertThat(response.fullName()).isEqualTo("Jaime Medina");

            var captor = ArgumentCaptor.forClass(StudentEntity.class);
            verify(studentRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(StudentStatus.ACTIVE);
        }

        @Test
        void rejectsADuplicateEmailWithoutTouchingTheRepository() {
            when(studentRepository.existsByEmailIgnoreCase("a@academy.io")).thenReturn(true);

            assertThatThrownBy(() -> studentService.create(createRequest("a@academy.io")))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("email");

            verify(studentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("read")
    class Read {

        @Test
        void getByIdMapsDerivedAgeAndFullName() {
            when(studentRepository.findById(1L)).thenReturn(Optional.of(student(1L, "a@academy.io", StudentStatus.ACTIVE)));

            var response = studentService.getById(1L);

            assertThat(response.fullName()).isEqualTo("Jaime Medina");
            assertThat(response.age()).isEqualTo(java.time.Period.between(BIRTH_DATE, LocalDate.now()).getYears());
        }

        @Test
        void getByIdThrowsWhenAbsent() {
            when(studentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> studentService.getById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Student not found");
        }

        @Test
        void getByEmailThrowsWhenAbsent() {
            when(studentRepository.findByEmailIgnoreCase("nobody@academy.io")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> studentService.getByEmail("nobody@academy.io"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void findAllWithoutAStatusFilterListsEveryStudent() {
            Pageable pageable = PageRequest.of(0, 10);
            when(studentRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(student(1L, "a@academy.io", StudentStatus.INACTIVE))));

            var page = studentService.findAll(null, pageable);

            assertThat(page.getContent()).hasSize(1);
            verify(studentRepository).findAll(pageable);
            verify(studentRepository, never()).findByStatus(any(), any());
        }

        @Test
        void findAllWithAStatusFilterDelegatesToTheFilteredQuery() {
            Pageable pageable = PageRequest.of(0, 10);
            when(studentRepository.findByStatus(StudentStatus.ACTIVE, pageable))
                    .thenReturn(new PageImpl<>(List.of(student(1L, "a@academy.io", StudentStatus.ACTIVE))));

            var page = studentService.findAll(StudentStatus.ACTIVE, pageable);

            assertThat(page.getContent()).hasSize(1);
            verify(studentRepository, never()).findAll(any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void appliesTheNewDetails() {
            var existing = student(1L, "a@academy.io", StudentStatus.ACTIVE);
            when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(studentRepository.existsByEmailIgnoreCaseAndIdNot("new@academy.io", 1L)).thenReturn(false);

            var response = studentService.update(1L, new UpdateStudentRequest("Ana", "Lopez", "new@academy.io"));

            assertThat(response.firstName()).isEqualTo("Ana");
            assertThat(response.lastName()).isEqualTo("Lopez");
            assertThat(response.email()).isEqualTo("new@academy.io");
            assertThat(existing.getEmail()).isEqualTo("new@academy.io");
        }

        @Test
        void rejectsAnEmailOwnedByAnotherStudentAndLeavesTheEntityUntouched() {
            var existing = student(1L, "a@academy.io", StudentStatus.ACTIVE);
            when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(studentRepository.existsByEmailIgnoreCaseAndIdNot("taken@academy.io", 1L)).thenReturn(true);

            assertThatThrownBy(() -> studentService.update(1L,
                    new UpdateStudentRequest("Ana", "Lopez", "taken@academy.io")))
                    .isInstanceOf(DuplicateResourceException.class);

            assertThat(existing.getEmail()).isEqualTo("a@academy.io");
            assertThat(existing.getFirstName()).isEqualTo("Jaime");
        }
    }

    @Nested
    @DisplayName("deletion")
    class Deletion {

        @Test
        void deactivateIsASoftDeleteThatKeepsTheRow() {
            var existing = student(1L, "a@academy.io", StudentStatus.ACTIVE);
            when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));

            var response = studentService.deactivate(1L);

            assertThat(response.status()).isEqualTo(StudentStatus.INACTIVE);
            assertThat(existing.getStatus()).isEqualTo(StudentStatus.INACTIVE);
            verify(studentRepository, never()).delete(any());
        }

        @Test
        void hardDeleteIsRejectedWhileActiveEnrollmentsRemain() {
            when(studentRepository.findById(1L)).thenReturn(Optional.of(student(1L, "a@academy.io", StudentStatus.ACTIVE)));
            when(enrollmentRepository.existsByStudentIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(true);

            assertThatThrownBy(() -> studentService.delete(1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("active enrollments");

            verify(studentRepository, never()).delete(any());
        }

        @Test
        void hardDeleteIsRejectedWhenOnlyHistoricalEnrollmentsRemain() {
            when(studentRepository.findById(1L)).thenReturn(Optional.of(student(1L, "a@academy.io", StudentStatus.ACTIVE)));
            when(enrollmentRepository.existsByStudentIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(false);
            when(enrollmentRepository.existsByStudentId(1L)).thenReturn(true);

            assertThatThrownBy(() -> studentService.delete(1L))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("history");

            verify(studentRepository, never()).delete(any());
        }

        @Test
        void hardDeleteProceedsForAStudentWithNoEnrollments() {
            var existing = student(1L, "a@academy.io", StudentStatus.ACTIVE);
            when(studentRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(enrollmentRepository.existsByStudentIdAndStatus(1L, EnrollmentStatus.ENROLLED)).thenReturn(false);
            when(enrollmentRepository.existsByStudentId(1L)).thenReturn(false);

            studentService.delete(1L);

            verify(studentRepository).delete(existing);
        }
    }
}
