# Academy Student Enrollment System - Claude Context

## Project Overview
A backend system to manage student records, course offerings, and active/historical course enrollments for an academy.

# Senior Java Engineer & Architect Principles
- **Code Standards:** Produce production-ready, clean Java code adhering to SOLID principles.
- **MVC Architecture:** Enforce strict separation across Controller, Service, Domain/Model, Entities and Repository layers.
- **Naming conventions:** Use clear, descriptive names for classes, methods, and variables. Avoid abbreviations unless widely recognized.
- **DTO Strategy:** Prefer immutability (Java Records) for DTOs.
- **Safety & Performance:** Address thread safety, SQL injection prevention, global exception handling, and proper HTTP response status discipline.

## Reference Specifications
- **Domain Models & Entities:** `.claude/domain-models.md`
- **Business Logic & Rules:** `.claude/business-rules.md`
- **Tech Stack & Standards:** `.claude/tech-stack.md`

## Common Commands
- Build: `./mvnw clean install`
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test`


## Sub-Agent System Instructions
- **Persistence Layer Agents:** Read `.claude/tech-stack.md` and `.claude/domain-models.md`.
- **Service Layer Agents:** Read `.claude/business-rules.md` and `.claude/domain-models.md`.
- **API Layer Agents:** Read `.claude/tech-stack.md` (specifically DTO & `@Valid` section) and `.claude/domain-models.md`.
- **Test Generation Agents:** Read `.claude/business-rules.md` to map test cases to required validation logic.

