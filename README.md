# chat-gateway — gateway del sistema Chat

Servicio backend en **Spring Boot 4 / Java 25** con arquitectura MVC por capas. Es el
**único punto de entrada** del sistema: el cliente (frontend, app móvil) le habla siempre por
**REST** o, para el chat en tiempo real, **WebSocket**; el gateway valida lo que recibe y lo
reenvía al microservicio correspondiente por **gRPC** (protocolo interno, no expuesto al
cliente).

```
Cliente ──REST───────▶ chat-gateway ──gRPC (unario)────▶ chat-registro
Cliente ──WebSocket──▶ chat-gateway ──gRPC (bidi stream)▶ chat-conversacion
                                    ──gRPC──────────────▶ (futuros microservicios)
```

Ver [`docs/arquitectura-gateway.md`](docs/arquitectura-gateway.md) para el patrón completo y
cómo añadir un microservicio nuevo.

## Stack

| Área | Elección |
|------|----------|
| Framework | Spring Boot 4.1.1 (`spring-boot-starter-webmvc` + `spring-boot-starter-websocket`) |
| Lenguaje | Java 25 (toolchain de Gradle) |
| Build | Gradle (wrapper incluido) |
| Protocolo con el cliente | REST/JSON y WebSocket (chat en tiempo real) |
| Protocolo con los microservicios | gRPC (`io.grpc` + `com.google.protobuf` gradle plugin), unario y bidi streaming |
| Validación | Bean Validation (`spring-boot-starter-validation`) |
| Errores | RFC 9457 *Problem Details* vía `@RestControllerAdvice` |
| Docs API | springdoc-openapi + Swagger UI (contrato REST expuesto al cliente) |
| Observabilidad | Spring Boot Actuator |
| Utilidades | Lombok, DevTools |

El gateway **no tiene base de datos propia**: no persiste nada, solo enruta. Toda persistencia
vive en el microservicio destino (p. ej. `chat_registro` en la MySQL centralizada, gestionada
por `chat-registro`).

## Flujo de registro (vía `chat-registro`)

`POST /api/v1/registro`

```json
{ "username": "mateo", "email": "mateo@example.com", "password": "Passw0rd!23" }
```

- `username`: 3–50 caracteres, `[a-zA-Z0-9._-]`, único (sin distinguir mayúsculas).
- `email`: formato válido, ≤255, único (se normaliza a minúsculas).
- `password`: 8–20 caracteres; mayúscula + minúscula + número + carácter especial, sin 4+
  repetidos seguidos. El gateway solo la transporta hasta `chat-registro` por gRPC: no la
  persiste ni la loguea en ningún punto.

El gateway valida el cuerpo (mismas reglas que `chat-registro`) y, si pasa, llama por gRPC a
`RegistroGrpcService/Registrar`. Respuestas: `201` con el usuario creado · `409` genérico si
los datos entran en conflicto · `400` con `errors[]` si la validación falla · `503` si
`chat-registro` no está disponible.

El contrato completo (esquemas, ejemplos y códigos de respuesta) está documentado con
anotaciones OpenAPI en la interfaz `RegistroApi` (que implementa el controlador) y en los DTO,
y se explora desde Swagger UI.

## Flujo de chat (vía `chat-conversacion`)

`GET /ws/chat/{usuario}` (upgrade a WebSocket) — `{usuario}` es el `username` de
`chat-registro` (3–50 caracteres, `[a-zA-Z0-9._-]`); un formato inválido rechaza el *handshake*
con `400` sin llegar a abrir nada por gRPC. Al conectar, el gateway abre un stream
`ConversacionGrpcService/Chat` hacia `chat-conversacion` identificado con esa misma cabecera de
metadata `usuario`, y traduce cada frame en los dos sentidos mientras la sesión siga abierta:

```json
{ "destinatario": "ana", "contenido": "Hola!" }
```

`GET /api/v1/conversaciones/{usuarioA}/{usuarioB}?page&size&sort` — historial paginado,
enrutado por una llamada unaria `ConversacionGrpcService/Historial`.

Contrato completo (formato de los mensajes, reglas de entrega, paginación) en
[`docs/contratos-api-conversacion.md`](docs/contratos-api-conversacion.md).

## Documentación de la API

- **Contratos para clientes** → [`docs/contratos-api.md`](docs/contratos-api.md) (registro de
  usuarios) y [`docs/contratos-api-conversacion.md`](docs/contratos-api-conversacion.md) (chat:
  WebSocket + historial) — request/response, errores, notas de integración, modelos TypeScript.
- **Arquitectura del gateway** → [`docs/arquitectura-gateway.md`](docs/arquitectura-gateway.md)
  (cómo se enruta cada petición, cómo añadir un microservicio nuevo — REST-unario o
  WebSocket-bidi).

Con la aplicación levantada (`./gradlew bootRun`):

- **Swagger UI** → <http://localhost:8080/swagger-ui.html>
- **OpenAPI JSON** → <http://localhost:8080/v3/api-docs>

## Estructura

```
com.arquetipo.demo
├── DemoApplication.java
├── common/                              infraestructura transversal
│   ├── config/CorsConfig.java · CorsProperties.java   CORS para /api/** (el gateway habla con el navegador)
│   ├── exception/
│   │   ├── ResourceNotFoundException        → 404
│   │   ├── DuplicateResourceException       → 409
│   │   ├── ValidationException              → 400 con errors[] (espejo de Bean Validation, vía gRPC)
│   │   └── ServiceUnavailableException      → 503 (microservicio destino caido)
│   └── web/GlobalExceptionHandler.java      excepciones → Problem Details (RFC 9457)
├── registro/                            enrutado hacia chat-registro (REST unario)
│   ├── web/
│   │   ├── RegistroController.java          POST /api/v1/registro (valida + delega)
│   │   ├── RegistroApi.java                 contrato OpenAPI
│   │   └── dto/RegistroRequest.java · RegistroResponse.java
│   ├── service/RegistroService.java         orquesta; hoy solo delega en el cliente gRPC
│   └── grpc/
│       ├── RegistroGrpcProperties.java          host/puerto de chat-registro (application.yml)
│       ├── RegistroGrpcClientConfig.java         ManagedChannel + stub como beans
│       └── RegistroGrpcClient.java               DTO <-> proto, errores gRPC <-> excepciones de dominio
└── conversacion/                        enrutado hacia chat-conversacion (WebSocket bidi + REST unario)
    ├── web/
    │   ├── ChatWebSocketConfig.java          registra el handler en /ws/chat/{usuario}
    │   ├── ChatWebSocketHandler.java         puente: frame de texto <-> stream de gRPC
    │   ├── UsuarioHandshakeInterceptor.java  valida el {usuario} de la URL antes de abrir el stream
    │   ├── ConversacionController.java       GET /api/v1/conversaciones/{usuarioA}/{usuarioB}
    │   ├── ConversacionApi.java              contrato OpenAPI del historial
    │   └── dto/MensajeEntrante.java · MensajeResponse.java · PageResponse.java
    ├── service/ConversacionService.java     orquesta; delega en el cliente gRPC
    └── grpc/
        ├── ConversacionGrpcProperties.java       host/puerto de chat-conversacion (application.yml)
        ├── ConversacionGrpcClientConfig.java      ManagedChannel + stub async (Chat) y bloqueante (Historial)
        └── ConversacionGrpcClient.java            DTO <-> proto (stream y unario), errores gRPC <-> excepciones

src/main/proto/registro.proto             copia exacta del contrato gRPC de chat-registro
src/main/proto/conversacion.proto         copia exacta del contrato gRPC de chat-conversacion
```

Flujo de una petición: `Controller` (valida) → `Service` (orquesta) → `GrpcClient` (llama al
microservicio y traduce la respuesta/error). El cliente REST nunca ve un mensaje proto.

## Arrancar

```bash
./gradlew bootRun
```

> Gradle necesita un JDK 17+ para ejecutarse y la toolchain compila con Java 25. Si tu
> `JAVA_HOME` apunta a un JDK antiguo, ajústalo o descomenta `org.gradle.java.home` en
> `gradle.properties`.
>
> Necesita a `chat-registro` (gRPC en `localhost:9090`) y a `chat-conversacion` (gRPC en
> `localhost:9091`) corriendo para que sus endpoints completen con éxito; si alguno no está,
> el gateway responde `503` solo en las llamadas que dependen de él. El propio arranque del
> gateway no depende de ninguno: los canales gRPC conectan de forma perezosa.

### Variables de entorno

| Variable | Por defecto | Uso |
|----------|-------------|-----|
| `REGISTRO_GRPC_HOST` | `localhost` | Host gRPC de `chat-registro` |
| `REGISTRO_GRPC_PORT` | `9090` | Puerto gRPC de `chat-registro` |
| `CONVERSACION_GRPC_HOST` | `localhost` | Host gRPC de `chat-conversacion` |
| `CONVERSACION_GRPC_PORT` | `9091` | Puerto gRPC de `chat-conversacion` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Orígenes permitidos para `/api/**` |
| `CORS_ALLOW_CREDENTIALS` | `false` | Si se permiten cookies/credenciales en CORS |
| `WEBSOCKET_ALLOWED_ORIGINS` | `http://localhost:3000` | Orígenes permitidos para `/ws/**` (independiente de `CORS_ALLOWED_ORIGINS`) |

| Recurso | URL |
|---------|-----|
| Registro | `POST` http://localhost:8080/api/v1/registro |
| Chat (WebSocket) | ws://localhost:8080/ws/chat/{usuario} |
| Historial de chat | `GET` http://localhost:8080/api/v1/conversaciones/{usuarioA}/{usuarioB} |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Actuator health | http://localhost:8080/actuator/health |

Tests: `./gradlew test` · Empaquetar: `./gradlew bootJar` · Docker: `docker build -t chat-gateway .`

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
| `ValidationException` / Bean Validation (`@Valid`) | 400 con lista `errors` |
| `ServiceUnavailableException` | 503 |
| cualquier otra | 500 (mensaje genérico, traza solo en logs) |
