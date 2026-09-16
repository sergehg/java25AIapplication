# Domain Models & Data Schema

## Entity Relationship Overview
- **Student** 1 ── N **Enrollment**
- **Course** 1 ── N **Enrollment**
- **Enrollment** acts as a join table linking `Student` and `Course` with additional payload attributes (status, grade, enrollment date).

---

## Entities

### 1. Student (`students`)
Represents an enrolled individual in the academy.

| Field | Type | Modifiers / Annotations | Description |
|---|---|---|---|
| `id` | `Long` | `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)` | Primary key |
| `firstName` | `String` | `@Column(nullable = false, length = 50)` | Student's first name |
| `lastName` | `String` | `@Column(nullable = false, length = 50)` | Student's last name |
| `email` | `String` | `@Column(nullable = false, unique = true, length = 100)` | Unique contact email |
| `status` | `StudentStatus` | `@Enumerated(EnumType.STRING)`, `@Column(nullable = false)` | Enum: `ACTIVE`, `INACTIVE`, `GRADUATED` |
| `createdAt` | `LocalDateTime` | `@Column(nullable = false, updatable = false)` | Timestamp of creation |
| `enrollments` | `List<Enrollment>` | `@OneToMany(mappedBy = "student", cascade = CascadeType.ALL)` | Associated enrollments |

### 2. Course (`courses`)
Represents a course offered by the academy.

| Field | Type | Modifiers / Annotations | Description |
|---|---|---|---|
| `id` | `Long` | `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)` | Primary key |
| `code` | `String` | `@Column(nullable = false, unique = true, length = 20)` | E.g., "JAVA-25", "DB-101" |
| `title` | `String` | `@Column(nullable = false, length = 100)` | Course title |
| `description` | `String` | `@Column(length = 500)` | Detailed overview |
| `capacity` | `Integer` | `@Column(nullable = false)` | Max allowed active students |
| `status` | `CourseStatus` | `@Enumerated(EnumType.STRING)`, `@Column(nullable = false)` | Enum: `OPEN`, `CLOSED`, `ARCHIVED` |
| `enrollments` | `List<Enrollment>` | `@OneToMany(mappedBy = "course")` | Associated enrollments |

### 3. Enrollment (`enrollments`)
Represents the mapping between a student and a course.

| Field        | Type               | Modifiers / Annotations | Description                              |
|--------------|--------------------|---|------------------------------------------|
| `id`         | `Long`             | `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)` | Primary key                              |
| `student`    | `Student`          | `@ManyToOne(fetch = FetchType.LAZY)`, `@JoinColumn(name = "student_id", nullable = false)` | Reference to Student                     |
| `course`     | `Course`           | `@ManyToOne(fetch = FetchType.LAZY)`, `@JoinColumn(name = "course_id", nullable = false)` | Reference to Course                      |
| `enrolledAt` | `LocalDateTime`    | `@Column(nullable = false)` | Timestamp of enrollment                  |
| `status`     | `EnrollmentStatus` | `@Enumerated(EnumType.STRING)`, `@Column(nullable = false)` | Enum: `ENROLLED`, `COMPLETED`, `DROPPED` |
| `classroom`  | `String`           | `@Enumerated(EnumType.STRING)`, `@Column(nullable = false)` | Enum: `A`, `B`, `C`                      |


*Database Constraint:* Unique index on `(student_id, course_id)`.

---

## DTOs & `@Valid` Constraints

### `CreateStudentRequest`
- `firstName`: `@NotBlank`, `@Size(max = 50)`
- `lastName`: `@NotBlank`, `@Size(max = 50)`
- `email`: `@NotBlank`, `@Email`, `@Size(max = 100)`

### `CreateCourseRequest`
- `code`: `@NotBlank`, `@Size(max = 20)`
- `title`: `@NotBlank`, `@Size(max = 100)`
- `capacity`: `@NotNull`, `@Min(1)`, `@Max(500)`

### `EnrollStudentRequest`
- `studentId`: `@NotNull`
- `courseId`: `@NotNull`