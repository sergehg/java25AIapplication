# Tech Stack & Standards

## Environment & Core Stack
- **Language:** Java 25
- **Database:** H2 Database (In-Memory / File-based)
- **ORM / Persistence:** Jakarta Persistence API (JPA) / Hibernate
- **Boilerplate Reduction:** Lombok (`@Data`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`)
- **Validation:** Jakarta Validation / Hibernate Validator (`@Valid`, `@NotNull`, `@NotBlank`, `@Size`, `@Email`)

## Engineering Standards
=
- **Global Exception Handling:** Use `@RestControllerAdvice` to translate domain exceptions and `@Valid` constraint violations into standardized HTTP responses.

### Model / Entity Guidelines
- Use Lombok annotations to reduce getter/setter/constructor clutter.
- Always annotate JPA entities properly (`@Entity`, `@Table`, `@Id`, `@GeneratedValue`).
- Avoid using Lombok's `@Data` on JPA Entities if circular dependencies exist (prefer explicit `@Getter`, `@Setter`, and `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`).

### DTO & Input Validation
- All incoming payload DTOs must use validation annotations (`@NotBlank`, `@NotNull`, `@Email`, etc.).
- Controller/Service methods handling mutation payloads **must** use `@Valid` to enforce request validation before processing logic.

### Database (H2)
- Database schema managed via JPA auto-DDL (`spring.jpa.hibernate.ddl-auto=update` or `create-drop` for dev/test).
- H2 Console enabled in development for quick inspection.