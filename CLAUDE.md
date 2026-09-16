# Academy Student Enrollment System - Claude Context

## Project Overview
A backend system to manage student records, course offerings, and active/historical course enrollments for an academy.

## Core Tech Stack
- **Java 25**
- **H2 Database**
- **JPA** (Persistence)
- **Lombok** (Code generation)
- **@Valid / Jakarta Validation** (Input validation)

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

