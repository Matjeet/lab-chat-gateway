# Arquitectura de chat-gateway

`chat-gateway` es el único punto de entrada del sistema: el cliente (frontend, app móvil) solo
le habla a él, por los protocolos que ya conoce un navegador — **REST** y, para el chat en
tiempo real, **WebSocket**. El gateway no implementa reglas de negocio propias — valida el
formato de lo que recibe y lo reenvía al microservicio correspondiente por **gRPC** (protocolo
interno, no expuesto al cliente).

```
Cliente ──REST───────▶ chat-gateway ──gRPC (unario)────▶ chat-registro
Cliente ──WebSocket──▶ chat-gateway ──gRPC (bidi stream)▶ chat-conversacion
                                    ──gRPC──────────────▶ (futuro microservicio N)
```

## Por qué REST/WebSocket fuera, gRPC dentro

- **Cliente ↔ gateway (REST/JSON, WebSocket):** los protocolos que ya conocen los clientes
  web/móvil — REST cacheable por proxies e inspeccionable con curl/Postman sin tooling
  adicional, WebSocket como lo que ya usaría un navegador para tiempo real sin depender de un
  cliente gRPC-Web.
- **Gateway ↔ microservicios (gRPC):** contratos tipados por `.proto`, generación de stubs,
  HTTP/2 multiplexado (incluido streaming bidi) — apropiado para tráfico interno de red
  controlada donde ambos lados son código propio.

El cliente **no sabe** que por debajo hay gRPC: el contrato JSON documentado en
`docs/contratos-api.md` no cambia si un día se sustituye el transporte interno por otra cosa.

Hay dos formas de "espejo" según el tipo de llamada gRPC del microservicio destino:

| Llamada gRPC | Protocolo con el cliente | Ejemplo |
|---|---|---|
| Unaria (`rpc X (Req) returns (Res)`) | REST: una petición, una respuesta | `registro/` → `chat-registro` |
| Bidi streaming (`rpc X (stream Req) returns (stream Res)`) | WebSocket: una conexión larga, frames en ambos sentidos | `conversacion/` → `chat-conversacion` |

## Paquete por microservicio destino

Cada microservicio al que el gateway enruta tiene su propia carpeta bajo
`com.arquetipo.demo`. Caso REST-unario (`registro/`):

```
registro/
├── web/
│   ├── RegistroController.java   REST: valida (@Valid) y delega en el service
│   ├── RegistroApi.java          contrato OpenAPI (igual que expone el propio microservicio)
│   └── dto/
│       ├── RegistroRequest.java  mismas anotaciones Bean Validation que el microservicio
│       └── RegistroResponse.java
├── service/
│   └── RegistroService.java      orquesta; hoy solo delega, es donde iria logica futura
│                                  (agregar datos de mas de un microservicio, etc.)
└── grpc/
    ├── RegistroGrpcProperties.java   host/puerto del microservicio (application.yml)
    ├── RegistroGrpcClientConfig.java  ManagedChannel + stub como beans de Spring
    └── RegistroGrpcClient.java       traduce DTO REST <-> mensaje proto, y errores gRPC
                                       <-> excepciones de dominio (com.arquetipo.demo.common.exception)
```

Caso WebSocket-bidi (`conversacion/`) — misma forma, con `web/` haciendo de puente en vez de
traducir una sola petición/respuesta:

```
conversacion/
├── web/
│   ├── ChatWebSocketConfig.java          registra el handler en /ws/chat/{usuario}
│   ├── ChatWebSocketHandler.java         puente: frame de texto <-> stream gRPC (ver mas abajo)
│   ├── UsuarioHandshakeInterceptor.java  valida el {usuario} de la URL antes de abrir el stream
│   ├── ConversacionController.java       REST del historial (unario, mismo patron que registro)
│   ├── ConversacionApi.java              contrato OpenAPI del historial
│   └── dto/ (MensajeEntrante, MensajeResponse, PageResponse)
├── service/
│   └── ConversacionService.java          delega en el grpc client; un metodo por rpc (uno
│                                          bidi, uno unario)
└── grpc/
    ├── ConversacionGrpcProperties.java
    ├── ConversacionGrpcClientConfig.java  dos stubs sobre el mismo canal: uno async (bidi
    │                                      streaming, para Chat) y uno bloqueante (para Historial)
    └── ConversacionGrpcClient.java        abrirChat(usuario, receptor) devuelve un
                                            StreamObserver para mandar; historial(...) es una
                                            llamada bloqueante normal
```

`ChatWebSocketHandler` abre, en `afterConnectionEstablished`, un stream gRPC por sesión de
WebSocket (identificado con la cabecera de metadata `usuario`, igual que exige el
microservicio) y lo mantiene mientras la sesión siga abierta: cada frame de texto entrante se
valida y se reenvía por el stream (`onNext`); cada mensaje que llega por el stream se traduce
y se manda por el socket. Cerrar la sesión de WebSocket completa el stream (`onCompleted`); un
error del stream cierra la sesión. La sesión se envuelve en
`ConcurrentWebSocketSessionDecorator` porque quien escribe en ella es el hilo del cliente
gRPC, no el hilo propio de la sesión.

`src/main/proto/<servicio>.proto` es una **copia exacta** del `.proto` que documenta el
microservicio destino (ver `contrato-grpc-<servicio>.md` en su propio repo) — no se reescribe,
solo se copia tal cual para que el gradle plugin `com.google.protobuf` genere el stub.

## Autenticación: el gateway es la única frontera

`GET /api/v1/usuarios/{uid}` (dentro de `registro/`, junto al alta) es el primer endpoint
autenticado del sistema, y fija la regla para todos los que vengan después:

> **El gateway valida los tokens de identidad. Los microservicios internos no.**

Concretamente, `chat-gateway` tiene su **propia** integración con Firebase Admin SDK (mismo
proyecto de Firebase que usa `chat-registro` para crear cuentas, pero una dependencia
`firebase-admin` distinta, en `common/auth/firebase/`) — es el único servicio del sistema que
la lleva con este propósito. `chat-registro` (y cualquier microservicio futuro) **no** verifica
tokens: recibe del gateway un identificador ya autenticado (p. ej. un `uid`) y confía en él,
igual que confiaría en un dato que él mismo hubiera calculado.

```
common/auth/
├── VerificadorTokenIdentidad.java   puerto: verificar(idToken) -> uid, o UnauthorizedException
├── AutenticacionExtractor.java      lo usa cualquier controlador: uidAutenticado(cabecera Authorization)
└── firebase/
    ├── FirebaseAuthConfig.java              inicializa FirebaseApp/FirebaseAuth (solo para verificar)
    └── FirebaseVerificadorTokenIdentidad.java  implementacion sobre FirebaseAuth.verifyIdToken
```

`UsuarioController` es el ejemplo a seguir para cualquier endpoint nuevo que necesite
autenticación:

```java
String uidAutenticado = autenticacion.uidAutenticado(authorization); // 401 si falta o es invalido
if (!uidAutenticado.equals(uid)) {
    throw new ForbiddenException("...");                              // 403 si es de otro usuario
}
return service.obtenerUsuario(uid);                                   // a chat-registro solo llega el uid
```

**Por qué así, y no dejando que cada microservicio valide su propio token:**

- **Un solo lugar que puede fallar de forma insegura.** Si mañana se añade un microservicio
  nuevo que necesita saber "quién es el usuario autenticado", no se integra con Firebase (ni
  con OAuth, ni con lo que sea) — pide el uid ya verificado al gateway. Menos superficie con
  credenciales de proveedor de identidad, menos sitios que mantener actualizados si cambia el
  proveedor.
- **`VerificadorTokenIdentidad` es un puerto, no un acoplamiento a Firebase.** Cambiar de
  proveedor de identidad (o soportar varios) es escribir una implementación nueva de esa
  interfaz, sin tocar `AutenticacionExtractor` ni ningún controlador — mismo patrón que
  `ProveedorIdentidad` en `chat-registro`.
- **El token nunca sale del gateway.** Ni por gRPC ni en ningún log: los microservicios
  internos ven un `uid` (o el identificador que corresponda), nunca una credencial.

## Un patrón más: REST-unario autenticado

`GET /api/v1/usuarios/{uid}` es REST-unario como `Registrar`, pero además el propio
controlador autentica y autoriza antes de llamar por gRPC: extrae el `idToken` de
`Authorization`, lo verifica con `AutenticacionExtractor` (`401` si falta o es inválido),
compara el uid resultante contra el recurso pedido (`403` si no coincide), y solo entonces
delega en el `Service`/`GrpcClient` — que llaman a `chat-registro` con el `uid` desnudo, igual
que en cualquier otro rpc unario. Si un microservicio nuevo necesita algo similar, este es el
patrón a seguir.

## Cómo añadir un microservicio nuevo

1. Pide al equipo dueño del microservicio su `contrato-grpc-<servicio>.md` (o el `.proto`
   directamente) y su `contratos-api.md` si además define cómo debe verse expuesto al cliente.
2. Copia el `.proto` a `src/main/proto/<servicio>.proto`.
3. Crea el paquete `<servicio>/` con la misma forma que `registro/` (llamadas unarias → REST)
   o `conversacion/` (streaming bidi → WebSocket) de este documento, según lo que exponga el
   `rpc` del `.proto`.
4. Añade la dirección del microservicio a `application.yml` bajo `servicios.<servicio>` (host
   + puerto, ambos con variable de entorno) y su `Properties`/`ClientConfig` en `<servicio>/grpc/`
   — si hay una llamada bidi, el `ClientConfig` expone tanto el stub async como el bloqueante
   sobre el mismo canal (ver `ConversacionGrpcClientConfig`).
5. Para REST: traduce en `<servicio>Api`/`<servicio>Request`/`<servicio>Response` el mismo
   contrato JSON que el cliente ya espera. Para WebSocket: un `<Servicio>WebSocketHandler` que
   abra el stream al conectar y traduzca frames en los dos sentidos (ver
   `ChatWebSocketHandler`), más un `HandshakeInterceptor` si hace falta validar algo de la URL
   de conexión antes de abrir el stream. Si el endpoint necesita autenticación, inyecta
   `AutenticacionExtractor` (ver "Autenticación: el gateway es la única frontera" más arriba) —
   **nunca** integres el microservicio nuevo (ni este controlador) directamente con Firebase u
   otro proveedor de identidad para validar tokens.
6. Mapea los códigos gRPC (`ALREADY_EXISTS`, `INVALID_ARGUMENT`, `UNAVAILABLE`, ...) a las
   excepciones de `com.arquetipo.demo.common.exception` que ya traduce `GlobalExceptionHandler`
   — reutilízalas en vez de crear un manejador nuevo por servicio, salvo que el microservicio
   introduzca un tipo de error genuinamente nuevo. Para WebSocket no hay `GlobalExceptionHandler`
   equivalente (el protocolo no tiene Problem Details): un error del stream simplemente cierra
   la sesión (ver `ChatWebSocketHandler`).
7. Documenta el endpoint nuevo como una sección más de `docs/contratos-api.md` — es el único
   contrato REST/WebSocket del gateway, deliberadamente no partido por microservicio (un
   cliente solo debería necesitar abrir un documento para saber todo lo que el gateway expone).

## Manejo de errores

`GlobalExceptionHandler` (en `common/web/`) es el único lugar que traduce excepciones a
Problem Details (RFC 9457); ningún controlador atrapa excepciones. Las excepciones de dominio
que ya cubre y que cualquier cliente gRPC nuevo puede reutilizar:

| Excepción | Origen típico | HTTP |
|---|---|---|
| `DuplicateResourceException` | gRPC `ALREADY_EXISTS` | 409 |
| `ValidationException` | gRPC `INVALID_ARGUMENT` | 400 (con `errors[]`) |
| `UnauthorizedException` | El propio gateway (`AutenticacionExtractor`/`VerificadorTokenIdentidad`) — nunca un microservicio interno | 401 |
| `ForbiddenException` | El propio controlador, al comparar el uid autenticado contra el recurso pedido | 403 |
| `ServiceUnavailableException` | gRPC `UNAVAILABLE` | 503 |
| `ResourceNotFoundException` | gRPC `NOT_FOUND` | 404 |
| (cualquier otra) | gRPC `INTERNAL` u otro no mapeado | 500 genérico |

## Qué el gateway deliberadamente no hace

- **No persiste nada.** No tiene base de datos propia; toda persistencia vive en el
  microservicio destino.
- **No repite reglas de negocio** más allá de la validación de formato que necesita para
  devolver un 400 rápido sin gastar una llamada de red — la autoridad final sigue siendo el
  microservicio.
- **No reintenta llamadas automáticamente.** Un `503`/`500` se propaga al cliente; la política
  de reintentos (si se añade) sería explícita y documentada aparte.
- **No delega la validación de tokens de identidad en los microservicios internos.** Es al
  revés de lo que podría parecer natural en un sistema "sin lógica de negocio propia": la
  autenticación sí es responsabilidad exclusiva del gateway (ver "Autenticación: el gateway es
  la única frontera" más arriba) — un microservicio interno nunca debería recibir un token, solo
  un identificador ya verificado.
