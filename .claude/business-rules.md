# Business Rules & Validation Logic

## 1. CRUD Operations Scope

### Student CRUD
- **Create:** Register a new student. Initial status defaults to `ACTIVE`. Requires `@Valid` request payload.
- **Read:**
    - Fetch student by ID.
    - Search/List students with pagination and optional filter by `status`.
- **Update:** Modify basic details (`firstName`, `lastName`, `email`). Email changes must maintain uniqueness.
- **Delete:** Soft-delete preferred (mark `status = INACTIVE`). Hard delete is rejected if student has active `ENROLLED` records.

### Course CRUD
- **Create:** Define a new course code, title, description, and capacity. Initial status defaults to `OPEN`.
- **Read:**
    - Fetch course by ID or code.
    - List available courses filtered by `status` (OPEN/CLOSED/ARCHIVED).
- **Update:** Update title, description, or capacity. Reducing capacity below current active enrollment count is forbidden.
- **Delete:** Course can only be deleted/archived if no active `ENROLLED` students exist.


## 2. Student Management
- **Email Uniqueness:** Two students cannot share the same email address. Attempting to create a duplicate must throw a `DuplicateResourceException`.
- **Status Lifecycle:**
    - Default status on creation: `ACTIVE`.
    - Only `ACTIVE` students are permitted to enroll in new courses.

## 3. Course Management
- **Code Uniqueness:** Course codes must be unique (case-insensitive, e.g., `JAVA-25` == `java-25`).
- **Capacity Management:**
    - A course's `capacity` must be greater than zero.
    - When the count of `ENROLLED` students reaches `capacity`, the course status automatically shifts to `CLOSED`.

## 4. Enrollment Rules
- **Capacity Enforcement:** A student **cannot** be enrolled if current active enrollments equal or exceed course `capacity`. Throw `CourseFullException`.
- **Duplicate Active Enrollment:** A student cannot have more than one `ENROLLED` record for the same course concurrently. Throw `AlreadyEnrolledException`.
- **Re-enrollment Policy:** If a student previously dropped a course (`status = DROPPED`), creating a new enrollment updates the existing record status back to `ENROLLED` (rather than inserting a duplicate row).
- **Course Availability:** Students can only enroll in courses where `status = OPEN`.

## 5. Deletion & Retention Rules
- **Soft Deletion Preferred:** Do not execute SQL `DELETE` on records with historical value.
- **Student Removal:** A student with active `ENROLLED` courses cannot be deleted until those courses are marked `DROPPED` or `COMPLETED`.
- **Course Removal:** Courses with active enrollments cannot be deleted.

## 6. Validation Execution
- All incoming REST controller payloads mutating state (`POST`, `PUT`, `PATCH`) must enforce validation using `@Valid`.
- 

## API & Exception Handling Rules

### Global Exception Handling
- **Mechanism:** Implement a central `@RestControllerAdvice` component.
- **Handled Exceptions & Responses:**
  - `MethodArgumentNotValidException` (`@Valid` failures) → Return HTTP `400 Bad Request` with field error details.
  - Custom Domain Exceptions (`ResourceNotFoundException`, `CourseFullException`, `DuplicateResourceException`) → Return HTTP `400` or `404` with clean, readable error messages.
  - `Exception` (Unhandled errors) → Return HTTP `500 Internal Server Error` with a generic, safe error message.
- **Payload Format:** All error responses must follow a consistent JSON structure (`timestamp`, `status`, `error`, `message`, `path`).



## Functional Programming & Reporting Requirements

### 1. Student Age Sorting
- **Operation:** Retrieve all students and sort them in **descending order by age**.
- **Constraint:** Must be implemented using Java 25 Functional Programming constructs (Java Streams API, lambda expressions, `Comparator`).

### 2. Course-to-Student Mapping
- **Operation:** Group and display all enrolled courses alongside their enrolled students.
- **Data Structure:** Return/process as a `Map<String, List<String>>` (where key is the Course Title and value is the List of Student names).
- **Constraint:** Must use functional collectors (`Collectors.groupingBy`, `Collectors.mapping`, etc.).
- **Expected Output Format:**
  ```text
  Programming
    - Jaime Medina
  Database
    - Mito X