# chat-registro — microservicio de registro de usuarios

Servicio backend en **Spring Boot 4 / Java 25** con arquitectura MVC por capas.

Expone el **flujo de registro** (`POST /api/v1/registro`) que persiste usuarios en la
base de datos MySQL centralizada del sistema. El paquete `sample` conserva el recurso CRUD
de ejemplo (`Product`) del arquetipo; puede borrarse cuando ya no sirva de referencia
(paquete `com.arquetipo.demo.sample`, sus tests y `src/main/resources/data.sql`).

## Base de datos

Todos los microservicios comparten **una instancia MySQL** (localhost:3306). Cada servicio
tiene su **propio esquema** y su **propio usuario** con permisos solo sobre ese esquema.

| Servicio | Esquema | Usuario |
|----------|---------|---------|
| chat-registro | `chat_registro` | `chat_registro_svc` |

### Provisión (una sola vez, como `root`)

```bash
mysql -u root -p < src/main/resources/db/bootstrap.sql
```

Crea el esquema `chat_registro` y el usuario `chat_registro_svc` / `chat_registro_pw`.
El DDL de las tablas lo aplica Hibernate al arrancar (`spring.jpa.hibernate.ddl-auto=update`).

### Credenciales

`application.yml` trae valores por defecto para desarrollo local. En cualquier otro entorno
se sobreescriben por variables de entorno:

| Variable | Por defecto |
|----------|-------------|
| `DB_URL` | `jdbc:mysql://localhost:3306/chat_registro?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8` |
| `DB_USERNAME` | `chat_registro_svc` |
| `DB_PASSWORD` | `chat_registro_pw` |
| `JPA_DDL_AUTO` | `update` |

Los tests usan H2 en memoria (`src/test/resources/application.yml`); no necesitan MySQL.

## Flujo de registro

`POST /api/v1/registro`

```json
{ "username": "mateo", "email": "mateo@example.com", "password": "secretpass" }
```

- `username`: 3–50 caracteres, `[a-zA-Z0-9._-]`, único (sin distinguir mayúsculas).
- `email`: formato válido, ≤255, único (se normaliza a minúsculas).
- `password`: 8–100 caracteres; se guarda **solo el hash BCrypt**, nunca en claro.

Respuestas: `201` con el usuario creado (sin hash) · `409` si el username o el email ya
existen · `400` con lista `errors` si la validación falla.

```
com.arquetipo.demo.registro
├── config/PasswordEncoderConfig.java   bean PasswordEncoder (BCrypt)
├── domain/Usuario.java                 entidad JPA (tabla `usuarios`)
├── repository/UsuarioRepository.java   existsBy… / findBy… ignorando mayúsculas
├── mapper/UsuarioMapper.java           entidad → RegistroResponse
├── service/RegistroService.java        unicidad + hash + persistencia
└── web/
    ├── RegistroController.java          POST /api/v1/registro
    └── dto/RegistroRequest.java · RegistroResponse.java
```

Las excepciones de dominio (`DuplicateResourceException`) y el `GlobalExceptionHandler`
se reutilizan del paquete `sample` (infraestructura compartida).

---

## Arquetipo base MVC

Plantilla base para servicios backend con arquitectura MVC por capas. El paquete `sample`
contiene un recurso CRUD completo (`Product`) que se usa como plantilla: se copia, se
renombra para la entidad real y se ajustan los campos.

## Stack

| Área | Elección |
|------|----------|
| Framework | Spring Boot 4.1.1 (`spring-boot-starter-webmvc`) |
| Lenguaje | Java 25 (toolchain de Gradle) |
| Build | Gradle (wrapper incluido) |
| Persistencia | Spring Data JPA + Hibernate; MySQL (runtime), H2 en memoria (tests) |
| Contraseñas | BCrypt (`spring-security-crypto`) |
| Validación | Bean Validation (`spring-boot-starter-validation`) |
| Errores | RFC 9457 *Problem Details* vía `@RestControllerAdvice` |
| Docs API | springdoc-openapi + Swagger UI |
| Observabilidad | Spring Boot Actuator |
| Utilidades | Lombok, DevTools |

## Estructura

```
com.arquetipo.demo
├── DemoApplication.java
└── sample/                          ← plantilla de un recurso CRUD
    ├── config/JpaAuditingConfig.java    activa auditoría (createdAt/updatedAt)
    ├── domain/Product.java              entidad JPA (id + version + auditoría)
    ├── repository/ProductRepository.java  extiende JpaRepository + consultas propias
    ├── mapper/ProductMapper.java        entidad ⇄ DTO (mapeo manual)
    ├── service/ProductService.java      transacciones + reglas de negocio
    ├── exception/
    │   ├── ResourceNotFoundException    → 404
    │   └── DuplicateResourceException   → 409
    └── web/
        ├── ProductController.java       REST: list (paginado), get, post, put, delete
        ├── GlobalExceptionHandler.java  traduce excepciones a Problem Details
        └── dto/
            ├── ProductRequest.java      entrada (record + validación)
            ├── ProductResponse.java     salida (record)
            └── PageResponse.java        envoltorio de paginación estable
```

Flujo de una petición: `Controller` → `Service` (transacciones + reglas) → `Repository` (JPA)
→ `Entity`. El `Mapper` traduce entre `Entity` y los DTO `Request`/`Response`; el cliente
nunca ve la entidad.

## Arrancar

```bash
./gradlew bootRun
```

> Gradle necesita un JDK 17+ para ejecutarse y la toolchain compila con Java 25. Si tu
> `JAVA_HOME` apunta a un JDK antiguo, ajústalo o descomenta `org.gradle.java.home` en
> `gradle.properties`.
>
> Antes del primer arranque hay que provisionar el esquema (ver *Base de datos*).

| Recurso | URL |
|---------|-----|
| Registro | `POST` http://localhost:8080/api/v1/registro |
| API ejemplo (sample) | http://localhost:8080/api/v1/products |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Actuator health | http://localhost:8080/actuator/health |

Tests: `./gradlew test` · Empaquetar: `./gradlew bootJar` · Docker: `docker build -t mi-servicio .`

## Crear un recurso nuevo

Copia el paquete `sample` (o sus 6–8 clases) y renómbralo para tu entidad, p. ej. `Customer`:

1. **`domain/Customer`** — entidad `@Entity` con sus campos (mantén `id`, `version` y los
   campos de auditoría, o extrae una `@MappedSuperclass` si te cansa repetirlos).
2. **`web/dto/CustomerRequest` / `CustomerResponse`** — records; añade validación a `Request`.
3. **`repository/CustomerRepository extends JpaRepository<Customer, Long>`** — solo las
   consultas propias del dominio.
4. **`mapper/CustomerMapper`** — `@Component` con `toEntity` / `updateEntity` / `toResponse`.
5. **`service/CustomerService`** — `@Service @Transactional`; copia los 5 métodos CRUD de
   `ProductService` y cambia los tipos; ahí van las reglas de negocio.
6. **`web/CustomerController`** — `@RestController @RequestMapping("/api/v1/customers")`;
   copia los 5 endpoints. Endpoints a medida: añádelos como métodos normales.

`GlobalExceptionHandler` y las excepciones se comparten entre recursos: no hace falta duplicarlos.

## Reutilizar como base de un proyecto nuevo

1. Copia el directorio (sin `.git`, `.gradle`, `build`).
2. Renombra el paquete `com.arquetipo.demo` → el de tu proyecto (refactor del IDE).
3. Ajusta `group`, `description` en `build.gradle` y `rootProject.name` en `settings.gradle`.
4. Renombra `sample` a tu primer recurso y borra `src/main/resources/data.sql` (datos de ejemplo).
5. Actualiza `@OpenAPIDefinition` en `DemoApplication`.
6. `git init` y primer commit.

## Pasar a una base de datos real (perfil `prod`)

Añade el driver (p. ej. `runtimeOnly 'org.postgresql:postgresql'`) y crea `application-prod.yml`:

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate      # el esquema lo gestiona Flyway/Liquibase, no Hibernate
  sql:
    init:
      mode: never
```

Arranca con `SPRING_PROFILES_ACTIVE=prod`. Para versionar el esquema añade
`spring-boot-starter-flyway` (o Liquibase) con las migraciones en `src/main/resources/db/migration`.

## Contrato de errores

Todas las respuestas de error siguen RFC 9457:

```json
{
  "type": "urn:problem-type:validation-error",
  "title": "Datos invalidos",
  "status": 400,
  "detail": "El cuerpo de la peticion no supero la validacion",
  "instance": "/api/v1/products",
  "timestamp": "2026-01-01T10:00:00Z",
  "errors": [{ "field": "price", "message": "no debe ser nulo" }]
}
```

| Excepción | HTTP |
|-----------|------|
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` | 409 |
| Bean Validation (`@Valid`) | 400 con lista `errors` |
| `DataIntegrityViolationException` | 409 |
| cualquier otra | 500 (mensaje genérico, traza solo en logs) |
