# chat-registro — microservicio de registro de usuarios

Servicio backend en **Spring Boot 4 / Java 25** con arquitectura MVC por capas.
Expone el **flujo de registro** (`POST /api/v1/registro`) que persiste usuarios en la
base de datos MySQL centralizada del sistema.

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
Al adoptar Flyway/Liquibase, pasar a `validate` y versionar el DDL en
`src/main/resources/db/migration`.

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

## Estructura

```
com.arquetipo.demo
├── DemoApplication.java
├── common/                              infraestructura transversal
│   ├── config/JpaAuditingConfig.java        auditoría (createdAt/updatedAt)
│   ├── exception/
│   │   ├── ResourceNotFoundException        → 404
│   │   └── DuplicateResourceException       → 409
│   └── web/GlobalExceptionHandler.java      excepciones → Problem Details (RFC 9457)
└── registro/                            flujo de alta de usuarios
    ├── config/PasswordEncoderConfig.java    bean PasswordEncoder (BCrypt)
    ├── domain/Usuario.java                  entidad JPA (tabla `usuarios`)
    ├── repository/UsuarioRepository.java    existsBy… / findBy… ignorando mayúsculas
    ├── mapper/UsuarioMapper.java            entidad → RegistroResponse
    ├── service/RegistroService.java         unicidad + hash + persistencia
    └── web/
        ├── RegistroController.java          POST /api/v1/registro
        └── dto/RegistroRequest.java · RegistroResponse.java
```

Flujo de una petición: `Controller` → `Service` (transacciones + reglas) → `Repository` (JPA)
→ `Entity`. El `Mapper` traduce entre `Entity` y los DTO; el cliente nunca ve la entidad.

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
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Actuator health | http://localhost:8080/actuator/health |

Tests: `./gradlew test` · Empaquetar: `./gradlew bootJar` · Docker: `docker build -t chat-registro .`

## Contrato de errores

Todas las respuestas de error siguen RFC 9457:

```json
{
  "type": "urn:problem-type:validation-error",
  "title": "Datos invalidos",
  "status": 400,
  "detail": "El cuerpo de la peticion no supero la validacion",
  "instance": "/api/v1/registro",
  "timestamp": "2026-01-01T10:00:00Z",
  "errors": [{ "field": "email", "message": "debe ser una dirección de correo electrónico con formato correcto" }]
}
```

| Excepción | HTTP |
|-----------|------|
| `ResourceNotFoundException` | 404 |
| `DuplicateResourceException` | 409 |
| Bean Validation (`@Valid`) | 400 con lista `errors` |
| `DataIntegrityViolationException` | 409 |
| cualquier otra | 500 (mensaje genérico, traza solo en logs) |
