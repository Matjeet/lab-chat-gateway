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
`docs/contratos-api.md` (registro) y `docs/contratos-api-conversacion.md` (chat) no cambia si
un día se sustituye el transporte interno por otra cosa.

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
   de conexión antes de abrir el stream.
6. Mapea los códigos gRPC (`ALREADY_EXISTS`, `INVALID_ARGUMENT`, `UNAVAILABLE`, ...) a las
   excepciones de `com.arquetipo.demo.common.exception` que ya traduce `GlobalExceptionHandler`
   — reutilízalas en vez de crear un manejador nuevo por servicio, salvo que el microservicio
   introduzca un tipo de error genuinamente nuevo. Para WebSocket no hay `GlobalExceptionHandler`
   equivalente (el protocolo no tiene Problem Details): un error del stream simplemente cierra
   la sesión (ver `ChatWebSocketHandler`).
7. Documenta el endpoint nuevo en `docs/contratos-api.md` (o en un archivo aparte si la lista
   crece mucho, p. ej. `docs/contratos-api-<servicio>.md` — así se hizo para `conversacion/`).

## Manejo de errores

`GlobalExceptionHandler` (en `common/web/`) es el único lugar que traduce excepciones a
Problem Details (RFC 9457); ningún controlador atrapa excepciones. Las excepciones de dominio
que ya cubre y que cualquier cliente gRPC nuevo puede reutilizar:

| Excepción | Código gRPC de origen tipico | HTTP |
|---|---|---|
| `DuplicateResourceException` | `ALREADY_EXISTS` | 409 |
| `ValidationException` | `INVALID_ARGUMENT` | 400 (con `errors[]`) |
| `ServiceUnavailableException` | `UNAVAILABLE` | 503 |
| `ResourceNotFoundException` | `NOT_FOUND` | 404 |
| (cualquier otra) | `INTERNAL` u otro no mapeado | 500 genérico |

## Qué el gateway deliberadamente no hace

- **No persiste nada.** No tiene base de datos propia; toda persistencia vive en el
  microservicio destino.
- **No repite reglas de negocio** más allá de la validación de formato que necesita para
  devolver un 400 rápido sin gastar una llamada de red — la autoridad final sigue siendo el
  microservicio.
- **No reintenta llamadas automáticamente.** Un `503`/`500` se propaga al cliente; la política
  de reintentos (si se añade) sería explícita y documentada aparte.
