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
| Identidad | Firebase Admin SDK — el gateway es el **único** servicio del sistema que valida tokens (`Authorization: Bearer <idToken>`); ver `common/auth` |
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

`GET /api/v1/conversaciones/{usuario}/chats?cursor&size` (**autenticado**, `Authorization:
Bearer <idToken>`) — lista de chats de `usuario` con el último mensaje de cada uno, paginada
por **cursor** (no página/offset, pensada para scroll infinito), enrutada a
`ConversacionGrpcService/ListaChats`. Primer endpoint REST del gateway sin equivalente previo
en `chat-conversacion` (nació directo como rpc gRPC). Mismo mecanismo de autenticación que
`GET /api/v1/usuarios/{uid}` (ver más abajo): el gateway verifica el `idToken` él mismo y
además resuelve el `username` del uid autenticado contra `chat-registro`
(`RegistroService.obtenerUsuario`, la misma consulta del endpoint de abajo) para compararlo
con `{usuario}` — un token válido de otro usuario responde `403`.

`POST /api/v1/conversaciones/solicitudes` (**autenticado**) — crea una solicitud de chat,
paso previo obligatorio para poder chatear con alguien, enrutado a
`ConversacionGrpcService/CrearSolicitud`. Cuerpo `{ "solicitante": "mateo", "solicitado": "ana" }`;
`solicitante` **debe ser el usuario autenticado** (mismo mecanismo que la lista de chats: el
gateway resuelve el `username` del uid autenticado y lo compara — `403` si no coincide).
`404` si alguno de los dos no existe en `chat-registro`, `409` si ya existe una solicitud
entre ambos.

Contrato completo (formato de los mensajes, reglas de entrega, paginación) en
[`docs/contratos-api.md`](docs/contratos-api.md) §4.3, §4.4, §4.5 y §4.7.

## Consulta de datos de usuario (vía `chat-registro`, autenticada)

`GET /api/v1/usuarios/{uid}` con cabecera `Authorization: Bearer <idToken>` — **el primer
endpoint del gateway que exigió autenticación**. `{uid}` es el identificador que asigna
Firebase al crear la cuenta (no el `username`). **El gateway valida el `idToken` él mismo**,
con su propia integración con Firebase Admin SDK (`common/auth`, independiente de la que usa
`chat-registro` para crear cuentas): sin la cabecera, o con un token inválido/expirado,
responde `401` sin llamar por gRPC; si el token es válido pero de otro uid, `403` — también
sin llamar. Solo si todo coincide llama a `RegistroGrpcService/BuscarUsuarioPorUid` en
`chat-registro`, pasando el `uid` **desnudo, nunca el token**.

```json
{ "username": "mateo", "email": "mateo@example.com" }
```

Contrato completo (los códigos de error posibles, ejemplos, modelos TypeScript) en
[`docs/contratos-api.md`](docs/contratos-api.md) §4.2.

`GET /api/v1/usuarios/existe?username=mateo` (también autenticada) — comprueba si un
`username` ya está en uso, enrutado a `RegistroGrpcService/ExisteUsername`. A diferencia de
los demás endpoints autenticados, **no** compara la identidad del token contra el recurso
pedido: basta con cualquier `idToken` válido, porque la consulta es sobre *otro* usuario (p.
ej. antes de iniciar un chat con él), no sobre uno mismo.

```json
{ "existe": true }
```

Contrato completo en [`docs/contratos-api.md`](docs/contratos-api.md) §4.6. **Desde
2026-09-22, todo endpoint REST nuevo del gateway se asume autenticado por defecto** — ver
"Autenticación: el gateway es la única frontera" en
[`docs/arquitectura-gateway.md`](docs/arquitectura-gateway.md) para los tres patrones de
autorización ya establecidos (comparar por uid, por un identificador resuelto, o ninguna
comparación).

## Documentación de la API

- **Contratos para clientes** → [`docs/contratos-api.md`](docs/contratos-api.md) — los siete
  endpoints del gateway (registro, datos de usuario, disponibilidad de username, WebSocket de
  chat, historial, lista de chats, solicitud de chat): request/response, errores, notas de
  integración, modelos TypeScript.
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
│   ├── auth/                                el gateway es la UNICA frontera de autenticacion del sistema
│   │   ├── VerificadorTokenIdentidad.java       puerto: verificar(idToken) -> uid
│   │   ├── AutenticacionExtractor.java          lo usa cualquier controlador: uidAutenticado(Authorization)
│   │   └── firebase/
│   │       ├── FirebaseAuthConfig.java              inicializa FirebaseApp/FirebaseAuth (solo para verificar)
│   │       └── FirebaseVerificadorTokenIdentidad.java  implementacion sobre FirebaseAuth.verifyIdToken
│   ├── exception/
│   │   ├── ResourceNotFoundException        → 404
│   │   ├── DuplicateResourceException       → 409
│   │   ├── ValidationException              → 400 con errors[] (espejo de Bean Validation, vía gRPC)
│   │   ├── UnauthorizedException            → 401 (lo lanza el propio gateway, nunca un microservicio)
│   │   ├── ForbiddenException                → 403 (identidad autenticada no coincide con el recurso pedido)
│   │   └── ServiceUnavailableException      → 503 (microservicio destino caido)
│   └── web/GlobalExceptionHandler.java      excepciones → Problem Details (RFC 9457)
├── registro/                            enrutado hacia chat-registro (REST unario)
│   ├── web/
│   │   ├── RegistroController.java          POST /api/v1/registro (valida + delega)
│   │   ├── RegistroApi.java                 contrato OpenAPI del registro
│   │   ├── UsuarioController.java           GET /api/v1/usuarios/{uid} (autentica y compara uid) y
│   │   │                                     /existe (autentica, sin comparar), ambos con AutenticacionExtractor
│   │   ├── UsuarioApi.java                  contrato OpenAPI de los dos
│   │   └── dto/RegistroRequest.java · RegistroResponse.java · UsuarioResponse.java · ExisteUsernameResponse.java
│   ├── service/RegistroService.java         orquesta; hoy solo delega en el cliente gRPC
│   └── grpc/
│       ├── RegistroGrpcProperties.java          host/puerto de chat-registro (application.yml)
│       ├── RegistroGrpcClientConfig.java         ManagedChannel + stub como beans
│       └── RegistroGrpcClient.java               DTO <-> proto (Registrar + BuscarUsuarioPorUid + ExisteUsername), errores gRPC <-> excepciones de dominio
└── conversacion/                        enrutado hacia chat-conversacion (WebSocket bidi + REST unario)
    ├── web/
    │   ├── ChatWebSocketConfig.java          registra el handler en /ws/chat/{usuario}
    │   ├── ChatWebSocketHandler.java         puente: frame de texto <-> stream de gRPC
    │   ├── UsuarioHandshakeInterceptor.java  valida el {usuario} de la URL antes de abrir el stream
    │   ├── ConversacionController.java       GET /api/v1/conversaciones/{usuarioA}/{usuarioB} (sin auth),
    │   │                                      /{usuario}/chats y POST /solicitudes (autenticados, resuelven
    │   │                                      el username via RegistroService)
    │   ├── ConversacionApi.java              contrato OpenAPI de los tres
    │   └── dto/MensajeEntrante.java · MensajeResponse.java · PageResponse.java · ChatResumen.java ·
    │       CursorPage.java · SolicitudChatRequest.java · SolicitudChatResponse.java
    ├── service/ConversacionService.java     orquesta; delega en el cliente gRPC
    └── grpc/
        ├── ConversacionGrpcProperties.java       host/puerto de chat-conversacion (application.yml)
        ├── ConversacionGrpcClientConfig.java      ManagedChannel + stub async (Chat) y bloqueante (Historial, ListaChats, CrearSolicitud)
        └── ConversacionGrpcClient.java            DTO <-> proto (stream y unarios), errores gRPC <-> excepciones

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
>
> **`GET /api/v1/usuarios/{uid}` necesita Firebase configurado** (`FIREBASE_ENABLED=true` +
> `FIREBASE_CREDENTIALS_PATH` apuntando a una clave de cuenta de servicio real): sin eso el
> contexto de Spring no arranca (`AutenticacionExtractor` necesita un bean
> `VerificadorTokenIdentidad`). Con `FIREBASE_ENABLED=false` tampoco arranca — solo tiene
> sentido si aportas tú mismo un bean `VerificadorTokenIdentidad` alternativo (los tests lo
> hacen, ver `src/test/resources/application.yml`).

### Variables de entorno

| Variable | Por defecto | Uso |
|----------|-------------|-----|
| `REGISTRO_GRPC_HOST` | `localhost` | Host gRPC de `chat-registro` |
| `REGISTRO_GRPC_PORT` | `9090` | Puerto gRPC de `chat-registro` |
| `CONVERSACION_GRPC_HOST` | `localhost` | Host gRPC de `chat-conversacion` |
| `CONVERSACION_GRPC_PORT` | `9091` | Puerto gRPC de `chat-conversacion` |
| `FIREBASE_ENABLED` | `true` | Si el gateway inicializa su propia integración con Firebase para validar tokens (`GET /api/v1/usuarios/{uid}`) |
| `FIREBASE_CREDENTIALS_PATH` | *(vacío = ADC)* | Ruta a la clave de cuenta de servicio (mismo proyecto de Firebase que `chat-registro`, pero **no** el mismo fichero necesariamente — cualquier clave del mismo proyecto sirve para verificar) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Orígenes permitidos para `/api/**` |
| `CORS_ALLOW_CREDENTIALS` | `false` | Si se permiten cookies/credenciales en CORS |
| `WEBSOCKET_ALLOWED_ORIGINS` | `http://localhost:3000` | Orígenes permitidos para `/ws/**` (independiente de `CORS_ALLOWED_ORIGINS`) |

| Recurso | URL |
|---------|-----|
| Registro | `POST` http://localhost:8080/api/v1/registro |
| Chat (WebSocket) | ws://localhost:8080/ws/chat/{usuario} |
| Historial de chat | `GET` http://localhost:8080/api/v1/conversaciones/{usuarioA}/{usuarioB} |
| Lista de chats (autenticado) | `GET` http://localhost:8080/api/v1/conversaciones/{usuario}/chats |
| Crear solicitud de chat (autenticado) | `POST` http://localhost:8080/api/v1/conversaciones/solicitudes |
| Datos de usuario (autenticado) | `GET` http://localhost:8080/api/v1/usuarios/{uid} |
| Existe username (autenticado) | `GET` http://localhost:8080/api/v1/usuarios/existe |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Actuator health | http://localhost:8080/actuator/health |

Tests: `./gradlew test` · Empaquetar: `./gradlew bootJar` · Contenedor: ver más abajo.

## Contenedor (Docker / Podman)

El [`Dockerfile`](Dockerfile) hace un build multi-etapa: compila con Gradle sobre una imagen
JDK y corre el jar resultante sobre una imagen JRE más liviana, con un usuario no-root. Los
ejemplos usan `podman`, pero son intercambiables con `docker` (misma sintaxis).

### Construir la imagen

```bash
podman build -t chat-gateway .
```

### Levantar el contenedor

```bash
podman run --rm -p 8080:8080 chat-gateway
```

Publica el puerto `8080` (REST + WebSocket) del contenedor al mismo puerto del host.

> **`chat-registro`/`chat-conversacion` corriendo en el host, no en un contenedor.** Dentro del
> contenedor, `localhost` apunta al propio contenedor, no al host — los defaults de
> `REGISTRO_GRPC_HOST`/`CONVERSACION_GRPC_HOST` (`localhost`) no van a alcanzarlos. Usa el
> hostname especial que resuelve al host: `host.containers.internal` en Podman
> (`host.docker.internal` en Docker Desktop):
>
> ```bash
> podman run --rm -p 8080:8080 \
>   -e REGISTRO_GRPC_HOST=host.containers.internal \
>   -e CONVERSACION_GRPC_HOST=host.containers.internal \
>   chat-gateway
> ```
>
> Si en cambio los tres corren como contenedores en una misma red (`podman network create` /
> `docker network create` + `--network` en cada `run`), usa ahí el nombre de cada contenedor en
> vez del hostname especial.

Cualquier variable de la tabla de arriba (`CORS_ALLOWED_ORIGINS`, `WEBSOCKET_ALLOWED_ORIGINS`,
puertos gRPC...) se pasa igual, con `-e NOMBRE=valor`. La imagen **no** lleva ninguna
credencial de Firebase dentro — se monta en runtime, igual que en `chat-registro`:

```bash
podman run --rm -p 8080:8080 \
  -e FIREBASE_ENABLED=true \
  -e FIREBASE_CREDENTIALS_PATH=/run/secrets/firebase-service-account.json \
  -v /ruta/local/firebase-service-account.json:/run/secrets/firebase-service-account.json:ro \
  chat-gateway
```

### Verificar que arrancó

```bash
curl http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}
```

Un `503` en `/api/v1/registro` o en `/api/v1/conversaciones/**` con el contenedor recién
levantado es esperable si `chat-registro`/`chat-conversacion` todavía no están arriba o no se
les indicó el host correcto — no es un fallo del propio gateway (ver tabla de errores más
abajo).

Detener: `podman stop <container-id>` (o ejecuta con `--name chat-gateway` para referenciarlo
por nombre en vez de buscar el ID con `podman ps`).

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
| `UnauthorizedException` (endpoints autenticados: `/usuarios/{uid}`, `/usuarios/existe`, `/conversaciones/{usuario}/chats`, `/conversaciones/solicitudes`) | 401 |
| `ForbiddenException` (solo donde se compara identidad: `/usuarios/{uid}`, `/conversaciones/{usuario}/chats`, `/conversaciones/solicitudes` — no en `/usuarios/existe`) | 403 |
| `ServiceUnavailableException` | 503 |
| cualquier otra | 500 (mensaje genérico, traza solo en logs) |
