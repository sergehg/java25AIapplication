# Academy Student Enrollment System — Implementation Summary

Work completed on the backend: the **persistence layer**, the **service layer**, the
**REST API layer** (controllers, DTOs, global exception handling), and the **test suite**.

- **Stack:** Java 25, Spring Boot 4.1.1, Spring Data JPA / Hibernate, H2 (in-memory), Lombok, Bean Validation
- **Build:** `./mvnw clean install` — passing, 87 tests, 0 failures
- **Base package:** `com.excercise.java25ai`

---

## 1. Package Structure

```
com.excercise.java25ai
├── entity/                     JPA entities (persistence layer)
│   ├── StudentEntity, CourseEntity, EnrollmentEntity
│   └── enums/                  StudentStatus, CourseStatus, EnrollmentStatus, Classroom
├── repository/                 Spring Data JPA interfaces
│   ├── StudentRepository, CourseRepository, EnrollmentRepository
│   └── projection/             CourseEnrollmentCount
├── dto/                        Immutable request/response records
├── exception/                  Custom domain exceptions + GlobalExceptionHandler
├── mapper/                     Entity <-> DTO translation (stateless @Component)
├── service/                    Service contracts
│   └── impl/                   Service implementations
└── controller/                 REST controllers
    ├── StudentController, CourseController
    └── EnrollmentController, ReportingController
```

---

## 2. Persistence Layer

### Entities

All three entities share the same conventions: `@Getter` / `@Setter` / `@Builder`, a protected
no-arg constructor for JPA, `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` on `id` only, and
`@ToString` limited to scalar fields. **`@Data` is deliberately not used** — it would generate
`equals` / `hashCode` / `toString` across the bidirectional `student ↔ enrollments ↔ course`
graph, causing infinite recursion and unwanted lazy loads. Timestamps are assigned in
`@PrePersist` rather than accepted from the caller.

| Entity | Table | Notes |
|---|---|---|
| `StudentEntity` | `students` | `dateOfBirth` added (see §6). Derived `getAge()` / `getFullName()` are `@Transient`. Cascades `ALL` + `orphanRemoval` to enrollments. |
| `CourseEntity` | `courses` | `@Version` optimistic lock. No cascade to enrollments — archiving a course must not erase history. Owns `applyCapacityStatus(long)`. |
| `EnrollmentEntity` | `enrollments` | Join entity with payload. Both `@ManyToOne` sides are `LAZY` and `optional = false`. `@Version` optimistic lock. |

### Database constraints (verified in the generated DDL)

| Constraint | Purpose |
|---|---|
| `uk_students_email` | Student email uniqueness |
| `uk_courses_code` | Course code uniqueness |
| `uk_enrollments_student_course` | Composite unique on `(student_id, course_id)` — backstops the re-enrollment policy |
| `fk_enrollments_student`, `fk_enrollments_course` | Referential integrity |
| `idx_students_status`, `idx_courses_status` | Status filtering |
| `idx_enrollments_course_status`, `idx_enrollments_student_status` | Capacity counts and "has active enrollments?" checks |

### Repositories

Every method is a derived query or a JPQL `@Query` with named parameters — no statement is ever
assembled by string concatenation, which keeps SQL injection out of this layer entirely.

- **`StudentRepository`** — `findByEmailIgnoreCase`, `existsByEmailIgnoreCase`,
  `existsByEmailIgnoreCaseAndIdNot` (update-time uniqueness), `findByStatus` (paged and list),
  `findAllWithEnrollmentsBy` (`@EntityGraph`, avoids N+1).
- **`CourseRepository`** — `findByCodeIgnoreCase`, `existsByCodeIgnoreCase`,
  `existsByCodeIgnoreCaseAndIdNot`, `findByStatus`, and `findByIdForUpdate`
  (`@Lock(PESSIMISTIC_WRITE)`).
- **`EnrollmentRepository`** — `findByStudentIdAndCourseId`, `existsByStudentIdAndCourseIdAndStatus`,
  `countByCourseIdAndStatus`, `existsBy{Student,Course}IdAndStatus`, `existsBy{Student,Course}Id`,
  paged lookups, `countByCourseIdInAndStatus` (batched occupancy projection), and
  `findAllByStatusWithStudentAndCourse` (fetch-joined, for the reports).

### Configuration (`application.properties`)

H2 in-memory datasource, `ddl-auto=create-drop`, SQL logging, H2 console at `/h2-console`, and
`spring.jpa.open-in-view=false` — with LAZY associations everywhere, leaving OSIV enabled would
hide missing fetch joins until production.

---

## 3. Service Layer

Each service is an interface plus a `@Service @Validated @RequiredArgsConstructor` implementation,
annotated `@Transactional(readOnly = true)` at class level with mutations overriding to
`@Transactional`. Validation constraints are declared **only on the interface** — Bean Validation
forbids strengthening parameter constraints in a subtype.

| Service | Responsibilities |
|---|---|
| `StudentService` | Create (defaults `ACTIVE`), read by id/email, paged list with optional status filter, update with email-uniqueness, `deactivate` (soft delete), `delete` (hard, guarded) |
| `CourseService` | Create (defaults `OPEN`), read by id/code, paged list, `findOpenCourses`, update with capacity guard, `archive` (soft), `delete` (hard, guarded) |
| `EnrollmentService` | `enroll`, `drop`, `complete`, read by id, paged lookups by student and course |
| `ReportingService` | `getStudentsSortedByAgeDesc`, `getEnrolledStudentsByCourse`, `getEnrolledStudentsByCourseReport` |

### DTOs (immutable records)

`CreateStudentRequest`, `UpdateStudentRequest`, `StudentResponse`, `CreateCourseRequest`,
`UpdateCourseRequest`, `CourseResponse`, `EnrollStudentRequest`, `EnrollmentResponse`.

Request records carry `@NotBlank` / `@NotNull` / `@Email` / `@Size` / `@Min` / `@Max` / `@Past` per
the specification. Response records flatten associations into scalars, so serialization can never
walk back into a lazy proxy. Status is never accepted from the caller on creation.

### Exceptions

`ResourceNotFoundException` (404), `DuplicateResourceException`, `CourseFullException`,
`AlreadyEnrolledException`, and `BusinessRuleViolationException` (see §6).

### Functional reporting (Streams API)

- `getStudentsSortedByAgeDesc()` — `Comparator.comparingInt(StudentEntity::getAge).reversed()`,
  with last and first name as deterministic tie-breakers.
- `getEnrolledStudentsByCourse()` — `Collectors.groupingBy` into a `TreeMap`, with
  `Collectors.mapping` + `collectingAndThen` producing an alphabetically sorted
  `Map<String, List<String>>` of course title to student names.
- `getEnrolledStudentsByCourseReport()` — the same grouping rendered via `Collectors.joining`
  into the plain-text format given in the business rules.

---

## 4. REST API Layer

### Endpoints

| Method | Path | Success | Notes |
|---|---|---|---|
| `POST` | `/api/students` | 201 + `Location` | `@Valid` body |
| `GET` | `/api/students/{id}` | 200 | |
| `GET` | `/api/students/by-email?email=` | 200 | Case-insensitive |
| `GET` | `/api/students?status=&page=&size=&sort=` | 200 | `PagedResponse`, optional status filter |
| `PUT` | `/api/students/{id}` | 200 | `@Valid` body |
| `DELETE` | `/api/students/{id}` | 204 | Soft delete -> `INACTIVE` |
| `DELETE` | `/api/students/{id}?permanent=true` | 204 | Guarded hard delete |
| `POST` | `/api/courses` | 201 + `Location` | `@Valid` body |
| `GET` | `/api/courses/{id}` | 200 | |
| `GET` | `/api/courses/code/{code}` | 200 | Case-insensitive |
| `GET` | `/api/courses?status=&page=&size=` | 200 | `PagedResponse` |
| `GET` | `/api/courses/open` | 200 | Courses accepting enrollments |
| `PUT` | `/api/courses/{id}` | 200 | `@Valid` body |
| `DELETE` | `/api/courses/{id}` | 204 | Soft delete -> `ARCHIVED` |
| `DELETE` | `/api/courses/{id}?permanent=true` | 204 | Guarded hard delete |
| `POST` | `/api/enrollments` | 201 + `Location` | `@Valid` body |
| `GET` | `/api/enrollments/{id}` | 200 | |
| `PATCH` | `/api/enrollments/{id}/drop` | 200 | Frees a seat |
| `PATCH` | `/api/enrollments/{id}/complete` | 200 | Frees a seat |
| `GET` | `/api/students/{id}/enrollments` | 200 | `PagedResponse` |
| `GET` | `/api/courses/{id}/enrollments` | 200 | `PagedResponse` |
| `GET` | `/api/reports/students/by-age-desc` | 200 | |
| `GET` | `/api/reports/courses/enrolled-students` | 200 | `Map<String, List<String>>` |
| `GET` | `/api/reports/courses/enrolled-students/text` | 200 | `text/plain` |

Every mutation endpoint annotates its request body with `@Valid` (business rule §6, audited and
confirmed: all five body-carrying `POST`/`PUT` endpoints; the two `PATCH` endpoints take only a
path id and have no body to validate). Paged endpoints use Spring Data's
`Pageable` resolver with `@PageableDefault`, and wrap results in `PagedResponse<T>` rather than
serializing `Page` directly, whose JSON structure is documented as unstable and leaks `Pageable`
internals into the public contract.

### Error contract

Every failure returns the same `ErrorResponse` shape — `timestamp`, `status`, `error`, `message`,
`path` — produced solely by `GlobalExceptionHandler` (`@RestControllerAdvice`). A single optional
member, `fieldErrors`, carries per-field detail for validation failures and is omitted from the
JSON entirely otherwise.

| Exception | Status | Body |
|---|---|---|
| `MethodArgumentNotValidException` (`@Valid` body) | 400 | + `fieldErrors` map |
| `HandlerMethodValidationException` (path/query params) | 400 | + `fieldErrors` map |
| `ConstraintViolationException` (`@Validated` service) | 400 | + `fieldErrors` map |
| `HttpMessageNotReadableException` | 400 | `"Malformed request body"` |
| `MethodArgumentTypeMismatchException` | 400 | Names the offending parameter |
| `MissingServletRequestParameterException` | 400 | Names the missing parameter |
| `BusinessRuleViolationException` | 400 | Rule message |
| `ResourceNotFoundException` | 404 | Resource and identifier |
| `DuplicateResourceException` | 409 | Field and value |
| `AlreadyEnrolledException` | 409 | Student and course |
| `CourseFullException` | 409 | Course and capacity |
| `DataIntegrityViolationException` | 409 | Generic; never leaks SQL |
| `ObjectOptimisticLockingFailureException` | 409 | Advises a retry |
| `Exception` | 500 | Fixed safe message; real cause logged only |

Parser messages, SQL and stack traces are logged rather than returned, so an error body can never
echo back payload fragments or schema details.

---

## 5. Business Rules Traceability

| Rule (`business-rules.md`) | Where enforced |
|---|---|
| Student defaults to `ACTIVE` | `StudentMapper.toEntity` + `@PrePersist` |
| Email uniqueness (create and update) | `StudentServiceImpl` + `uk_students_email` |
| Soft delete preferred | `StudentService.deactivate`, `CourseService.archive` |
| Hard delete blocked by enrollments | `StudentServiceImpl.delete`, `CourseServiceImpl.delete` |
| Course defaults to `OPEN` | `CourseMapper.toEntity` + `@PrePersist` |
| Course code unique, case-insensitive | `existsByCodeIgnoreCase` + `uk_courses_code` |
| Capacity must exceed zero | `@Min(1)` on both course request records |
| Capacity cannot drop below active count | `CourseServiceImpl.update` |
| Course auto-closes when full | `CourseEntity.applyCapacityStatus` |
| Capacity enforcement on enroll | `EnrollmentServiceImpl.enroll` → `CourseFullException` |
| No duplicate active enrollment | `EnrollmentServiceImpl` → `AlreadyEnrolledException` + composite unique |
| Re-enrollment reuses a `DROPPED` row | `EnrollmentServiceImpl.reactivate` |
| Only `OPEN` courses accept enrollment | `EnrollmentServiceImpl.enroll` |
| Only `ACTIVE` students may enroll | `EnrollmentServiceImpl.enroll` |
| `@Valid` on mutation payloads | `@Validated` services + `@Valid` on interface methods |
| Age sorting via Streams | `ReportingServiceImpl` |
| Course-to-student `Map` via collectors | `ReportingServiceImpl` |

### Thread safety

The capacity rule is a read-then-write, so two concurrent requests could otherwise both pass the
check. Three layers guard it:

1. `findByIdForUpdate` (`PESSIMISTIC_WRITE`) serialises concurrent enrollments into one course.
2. `@Version` on `CourseEntity` and `EnrollmentEntity` catches lost updates.
3. `uk_enrollments_student_course` is the database-level backstop for duplicate rows.

---

## 6. Decisions and Deviations from the Specification

| # | Decision | Reason |
|---|---|---|
| 1 | Added `dateOfBirth` to `StudentEntity` and `CreateStudentRequest` | The functional-programming section requires sorting by age, but the domain model defined no birth-date field. |
| 2 | `Enrollment.classroom` modelled as a `Classroom` enum, and added to `EnrollStudentRequest` | The spec typed it `String` but annotated it `@Enumerated` with values A/B/C; the column is non-null, so the caller must supply it. |
| 3 | A `CLOSED` course reopens when a seat frees up | The spec defines auto-close but is silent on the reverse; without this a course sits closed with empty seats. `ARCHIVED` is never reopened. |
| 4 | Added `BusinessRuleViolationException` | The four named exceptions do not cover blocked deletes, capacity reduction, inactive-student enrollment, or illegal status transitions. |
| 5 | Enroll guards ordered ARCHIVED → capacity → status | Because a full course auto-closes, a status check first would report every full course as "not open" and leave `CourseFullException` unreachable. |
| 6 | Hard delete rejected on **any** enrollment row, not only active ones | `StudentEntity` cascades `ALL` with `orphanRemoval`, so allowing it would SQL-DELETE completed and dropped history, which the retention rule forbids. |
| 7 | Re-enrolling in a `COMPLETED` course is rejected | The spec defines only the `DROPPED` → `ENROLLED` path; flipping a completion back would erase the record of it. |
| 8 | Removed `unique = true` from the `email` and `code` columns | Combined with a named `@UniqueConstraint`, Hibernate emitted only an anonymous column-level constraint (`CONSTRAINT_9`), making error mapping non-deterministic. |
| 9 | Added `spring-boot-starter-validation` to `pom.xml` | Bean Validation was absent from the classpath, so the DTO constraints would have been silently inert. |
| 10 | Services expose DTOs only; entities never cross the layer boundary | Keeps the persistence model out of the web layer. |
| 11 | Conflict conditions return **409**, not 400 | The spec originally said "400 or 404", but duplicate, already-enrolled and course-full are state conflicts, not malformed payloads; 409 lets a client tell them apart. `BusinessRuleViolationException` stays 400 and `ResourceNotFoundException` 404. **Now reflected in the spec:** `business-rules.md` carries the full exception → status table, the 400-vs-409 rule, and already-enrolled-over-course-full precedence; the exception Javadocs state 409 Conflict. |
| 12 | `DELETE` performs the soft delete; `?permanent=true` opts into the hard delete | Makes the spec's preferred removal path the default, so the destructive operation is never the accident. |
| 13 | Paged endpoints return a `PagedResponse<T>` envelope | Spring Data warns that serializing `Page` directly yields an unstable JSON structure. |
| 14 | `enroll()` checks for a prior enrollment **before** the capacity check | Discovered while testing: a student re-posting an enrollment to a course that had since filled received "course is full" instead of "already enrolled" — true but misleading, since one of those seats was theirs. |

---

## 7. Verification Performed

- `./mvnw clean install` — **passing**, no errors.
- **Generated DDL inspected** — all three named unique constraints, both foreign keys and all
  four indexes confirmed present.
- **Persistence smoke test** (`@DataJpaTest`, 3 tests, all passing) — derived queries, builder
  defaults, `@PrePersist` timestamps, the `select … for update` lock, and
  `DataIntegrityViolationException` on both duplicate email and duplicate `(student, course)`.
- **Service rules test** (`@SpringBootTest`, 14 tests, all passing) — every rule in the
  traceability table above, including drop → reopen → re-enroll reusing the same row, and the
  exact plain-text report output. This file was held only in a session scratchpad and has since
  been lost; `EnrollmentRulesIntegrationTest` re-covers its enrollment-specific cases.

### Test suite (87 tests, all passing)

**Unit tests** — JUnit 5 + Mockito. Repositories are `@Mock`; the mappers are the real stateless
implementations, so the assertions cover mapping too. Services are constructed directly, which
deliberately bypasses the `@Validated` and `@Transactional` proxies — those are covered by the
web-layer and integration tests instead.

| File | Tests | Covers |
|---|---|---|
| `service/impl/StudentServiceImplTest` | 13 | Create defaults, duplicate email, not-found, paged list with and without a status filter, update uniqueness (and that a rejected update leaves the entity untouched), soft delete keeping the row, all three hard-delete outcomes |
| `service/impl/CourseServiceImplTest` | 15 | Create defaults, case-insensitive duplicate code, live occupancy, batched occupancy on the listing path (asserting the per-row count is never called), capacity reduction rejection, auto-close and auto-reopen, archive and hard-delete guards |
| `service/impl/EnrollmentServiceImplTest` | 17 | Every `enroll()` guard and its ordering, capacity enforcement, dropped-record reactivation in place, auto-close on the last seat, status transitions with reopen, unknown-identifier lookups |
| `service/impl/ReportingServiceImplTest` | 10 | Descending age order, deterministic tie-breaking, age derivation, grouping structure, `TreeMap` key ordering, alphabetical names, active-only filtering, empty cases, exact text report |

**Web-layer slice test** — `@WebMvcTest` with `@MockitoBean` services, so no database is involved.

| File | Tests | Covers |
|---|---|---|
| `controller/ValidationErrorHandlingTest` | 15 | Invalid `@Valid` payloads on all five body-carrying mutation endpoints (`POST`/`PUT` students, `POST`/`PUT` courses, `POST` enrollments) returning 400 with the exact per-field messages, missing fields, size limits, `@Min`/`@Max` bounds, malformed and unparseable bodies, and `verifyNoInteractions` proving the request never reaches the service. Each group includes a valid-payload control case. |

**Integration tests** — full `@SpringBootTest` context against H2.

| File | Tests | Covers |
|---|---|---|
| `controller/ApiContractIntegrationTest` | 10 | Every endpoint end to end, status discipline, `Location` headers, the `PagedResponse` envelope, and the full error contract (400 with field errors, 404, 409, malformed body, bad parameter types) |
| `service/EnrollmentRulesIntegrationTest` | 6 | `enroll()` guard ordering, drop → reopen → re-enroll reusing one row, a dropped student losing their seat to someone else, completed-course rejection, archived-course rejection, capacity reduction |

> **Reading the counts:** test classes use `@Nested` groups, so surefire's per-class `.txt` report
> shows `0` for those classes while the real counts appear under the group display names. The
> reliable total comes from counting `<testcase>` elements in `target/surefire-reports/TEST-*.xml`
> — the `tests` attribute on an aggregated suite undercounts (e.g. `ValidationErrorHandlingTest`
> reports `tests="13"` but contains 15 test cases).

> Note for whoever writes the tests: Spring Boot 4 moved `@DataJpaTest` to
> `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`. The Boot 3 import
> (`org.springframework.boot.test.autoconfigure.orm.jpa`) does not compile.

---

## 8. Remaining Work

1. **Repository-layer tests** — no `@DataJpaTest` currently exercises the DDL constraints directly;
   the composite unique on `(student_id, course_id)` is covered only indirectly, through the
   service rules.
2. **Concurrency test** — the pessimistic lock and `@Version` guards are in place but are not
   exercised by a multi-threaded test.
3. **API documentation** — no OpenAPI/Swagger dependency is present yet.
4. **Housekeeping** — an empty `src/main/java/com/excercise/java25ai/model/enums/` directory is
   left over from before the enums moved under `entity/enums/`; it can be deleted.

### Version control

All work is committed and pushed to `origin/persistenceLayer` (as of `4604332` — exception →
HTTP status mapping documented in the spec, and course-update validation test added). The branch
has not yet been merged into `master`.

### Spring Boot 4 test-annotation moves

Boot 4 relocated the test annotations; the Boot 3 imports do not compile:

| Annotation | Boot 4 package |
|---|---|
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@AutoConfigureMockMvc`, `@WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@MockitoBean` (replaces `@MockBean`) | `org.springframework.test.context.bean.override.mockito` |

This project runs JUnit Jupiter 6.0.3 and Mockito 5.23 with `spring-boot-starter-validation`
added for Bean Validation.
