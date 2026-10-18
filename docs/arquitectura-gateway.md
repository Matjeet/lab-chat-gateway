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
│   ├── RegistroController.java   REST: valida (@Valid) y delega en el service (sin auth)
│   ├── RegistroApi.java          contrato OpenAPI (igual que expone el propio microservicio)
│   ├── UsuarioController.java    REST autenticado: obtenerUsuario (compara uid) y
│   │                              existeUsername (solo exige estar autenticado)
│   ├── UsuarioApi.java           contrato OpenAPI de ambos
│   └── dto/
│       ├── request/
│       │   └── RegistroRequest.java  mismas anotaciones Bean Validation que el microservicio
│       └── response/
│           ├── RegistroResponse.java
│           ├── UsuarioResponse.java
│           └── ExisteUsernameResponse.java
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
│   ├── websocket/                        todo lo del chat en tiempo real
│   │   ├── ChatWebSocketConfig.java          registra el handler en /ws/chat/{usuario}
│   │   ├── ChatWebSocketHandler.java         puente: frame de texto <-> stream gRPC (ver mas abajo)
│   │   └── UsuarioHandshakeInterceptor.java  valida el {usuario} de la URL antes de abrir el stream
│   ├── rest/                             todo lo REST
│   │   ├── ConversacionController.java       REST del historial (sin auth), la lista de chats y
│   │   │                                      crear/resolver una solicitud (autenticados, ver
│   │   │                                      "Autenticacion" mas abajo)
│   │   └── ConversacionApi.java              contrato OpenAPI
│   └── dto/                              compartido por rest y websocket
│       ├── request/ (MensajeEntrante, SolicitudChatRequest, ActualizarSolicitudRequest)
│       └── response/ (MensajeResponse, PageResponse, ChatResumen, CursorPage, SolicitudChatResponse)
├── service/
│   └── ConversacionService.java          delega en el grpc client; un metodo por rpc (uno
│                                          bidi, tres unarios)
└── grpc/
    ├── ConversacionGrpcProperties.java
    ├── ConversacionGrpcClientConfig.java  dos stubs sobre el mismo canal: uno async (bidi
    │                                      streaming, para Chat) y uno bloqueante (para
    │                                      Historial, ListaChats y CrearSolicitud)
    └── ConversacionGrpcClient.java        abrirChat(usuario, receptor) devuelve un
                                            StreamObserver para mandar; historial(...),
                                            listaChats(...) y crearSolicitud(...) son llamadas
                                            bloqueantes normales (el cursor de listaChats viaja
                                            tal cual, opaco)
```

Segundo caso REST-unario (`notificacion/`), misma forma que `registro/` — dos endpoints
autenticados con mecanismos de comparación distintos, ver más abajo:

```
notificacion/
├── web/
│   ├── NotificacionController.java   listaNotificaciones (compara username resuelto) y
│   │                                  actualizarLeida (compara uid directo, sin atar al recurso)
│   ├── NotificacionApi.java          contrato OpenAPI de ambos
│   └── dto/
│       ├── request/
│       │   └── MarcarLeidaRequest.java   uid (solo para comparar) + leida
│       └── response/
│           ├── NotificacionResponse.java
│           └── PageResponse.java         copia propia, mismo contrato que en registro/conversacion
├── service/
│   └── NotificacionService.java      orquesta; hoy solo delega
└── grpc/
    ├── NotificacionGrpcProperties.java
    ├── NotificacionGrpcClientConfig.java  un solo stub bloqueante (los dos rpc son unarios)
    └── NotificacionGrpcClient.java        traduce DTO REST <-> mensaje proto, y errores gRPC
                                            <-> excepciones de dominio
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

**Desde el 22-09-2026, todo endpoint REST nuevo del gateway se asume autenticado por
defecto** — `Authorization: Bearer <idToken>` obligatorio, verificado con
`AutenticacionExtractor`. Lo que varía de un endpoint a otro es **si además compara** esa
identidad contra el recurso pedido, y contra qué la compara: no hay una única respuesta,
decide caso por caso (ver los patrones más abajo: comparación directa por uid, comparación por
`username` resuelto, ninguna comparación, o uid directo sin atar al recurso concreto).

Concretamente, `chat-gateway` tiene su **propia** integración con Firebase Admin SDK (mismo
proyecto de Firebase que usa `chat-registro` para crear cuentas, pero una dependencia
`firebase-admin` distinta, en `common/auth/firebase/`) — es el único servicio del sistema que
la lleva con este propósito. `chat-registro` (y cualquier microservicio futuro) **no** verifica
tokens: recibe del gateway un identificador ya autenticado (p. ej. un `uid`) y confía en él,
igual que confiaría en un dato que él mismo hubiera calculado.

```
common/auth/
├── VerificadorTokenIdentidad.java   puerto: verificar(idToken) -> uid, o UnauthorizedException
├── AutenticacionExtractor.java      extrae y verifica el token: uidAutenticado(cabecera Authorization)
├── ValidadorAcceso.java             lo usan los controladores: autenticar / uidCoincide / usernameCoincide
└── firebase/
    ├── FirebaseAuthConfig.java              inicializa FirebaseApp/FirebaseAuth (solo para verificar)
    └── FirebaseVerificadorTokenIdentidad.java  implementacion sobre FirebaseAuth.verifyIdToken
```

`UsuarioController` es el ejemplo a seguir para cualquier endpoint nuevo que necesite
autenticación:

```java
if (!validador.uidCoincide(authorization, uid)) {   // 401 si falta o es invalido el token
    throw new ForbiddenException("...");              // 403 si es de otro usuario
}
return service.obtenerUsuario(uid);                   // a chat-registro solo llega el uid
```

**Cuando el recurso no lo identifica un uid** (`ConversacionController.listaChats`, sobre
`GET /api/v1/conversaciones/{usuario}/chats`; `ConversacionController.crearSolicitud`, sobre
`POST /api/v1/conversaciones/solicitudes`; y `ConversacionController.actualizarSolicitud`,
sobre `PATCH /api/v1/conversaciones/solicitudes`): el mismo patrón, con un paso intermedio. El
recurso está en `username` (de `chat-registro`), no en el uid que devuelve
`AutenticacionExtractor` — así que antes de comparar (`ValidadorAcceso.usernameCoincide`) hay que resolver uno a partir del otro,
siempre contra `chat-registro` (la única fuente de verdad de esa relación). No importa si lo
que se compara viene de la URL (`usuario`, un path variable) o del cuerpo (`solicitante` al
crear, `solicitado` al actualizar — un campo del JSON) — el patrón es el mismo. Lo que sí
importa es **contra qué campo** del cuerpo se compara cuando hay más de uno: `crearSolicitud`
compara contra `solicitante` (nadie solicita en nombre de otro), `actualizarSolicitud` contra
`solicitado` (solo quien recibió la solicitud puede resolverla, nunca quien la envió) — el
campo correcto lo decide el caso de uso, no una convención de nombres:

```java
if (!validador.usernameCoincide(authorization, usuario)) { // 401 si falta el token, 404 si no tiene perfil
    throw new ForbiddenException("...");                    // 403 si es de otro usuario
}
return service.listaChats(usuario, cursor, size);
```

Esto hace que `ValidadorAcceso` dependa de `RegistroService` (`common` →
`registro`, la única dependencia de `common` hacia una feature) — deliberado: el uid es
un dato de Firebase, el `username` es un dato de `chat-registro`, y solo `chat-registro` sabe
traducir entre los dos. Un microservicio futuro cuyo recurso también se identifique por algo
distinto al uid (un id propio, un slug, etc.) seguiría este mismo patrón: resolver primero
contra quien sea dueño de esa relación, comparar después.

**Cuando no hace falta ninguna comparación** (`UsuarioController.existeUsername`, sobre
`GET /api/v1/usuarios/existe`): el endpoint necesita saber que quien pregunta es *alguien*
autenticado, pero no que sea el dueño de *ese* recurso en concreto — comprobar si un
`username` existe es información sobre un tercero, no sobre uno mismo (piensa "¿puedo iniciar
un chat con él?"). Aquí basta con llamar a `ValidadorAcceso.autenticar`, que exige un token
válido y no compara contra nada:

```java
validador.autenticar(authorization);   // 401 si falta o es invalido; no se compara contra nada
return service.existeUsername(username);       // cualquier usuario autenticado puede preguntar por cualquier username
```

**Cuando compara por uid pero el recurso no está atado a ese uid**
(`NotificacionController.actualizarLeida`, sobre `PATCH /api/v1/notificaciones/{id}`): mismo
código que el primer patrón (comparación directa, sin resolver nada), pero con un límite
distinto — el `uid` de la petición no prueba que quien llama sea el dueño del recurso
concreto, porque `chat-notificaciones` no vincula todavía una notificación a un uid ni a un
`username`:

```java
if (!validador.uidCoincide(authorization, request.uid())) { // 401 si falta o es invalido el token
    throw new ForbiddenException("...");                     // 403 si es de otro usuario
}
return service.actualizarLeida(id, request.leida());          // "id" no se valida contra el uid
```

Esto **no es un error del gateway**: es un límite real del microservicio destino, documentado
como tal (`docs/contratos-api.md` §4.9) en vez de disimulado con una comparación que no
significaría nada. Si `chat-notificaciones` en el futuro vincula notificación → receptor de
forma verificable, este controlador pasaría al segundo patrón (comparación por `username`
resuelto), como `listaNotificaciones`.

No asumas que "autenticado" implica "compara contra algo", ni que "compara contra algo"
implica "verifica que seas el dueño del recurso concreto": son decisiones independientes.
Antes de implementar un endpoint nuevo, si no es evidente por el propio caso de uso, pregunta
qué corresponde — comparación directa por uid, comparación por un identificador resuelto (como
`username`), ninguna comparación, o uid directo sin garantía de propiedad sobre el recurso.

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
   de conexión antes de abrir el stream. **Todo endpoint REST nuevo se asume autenticado por
   defecto**: inyecta `ValidadorAcceso` (ver "Autenticación: el gateway es la única
   frontera" más arriba; nunca escribas la comparación de token/uid/username dentro del
   controlador) y, si no es evidente por el caso de uso, pregunta contra qué debe
   comparar esa identidad (uid, un identificador resuelto como `username`, ninguna comparación,
   o uid directo sin garantía de propiedad sobre el recurso — los patrones ya documentados)
   antes de implementarlo. **Nunca** integres
   el microservicio nuevo (ni este controlador) directamente con Firebase u otro proveedor de
   identidad para validar tokens: esa integración vive solo en el gateway.
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

## Estándares de código

### Constantes: nada de literales sueltos

Todo literal de texto o número con significado para la lógica o la configuración vive en
`common/Constantes.java`, nunca escrito en línea en el código que lo usa. Antes de crear una
constante nueva, busca si ya existe una con el mismo significado y reutilízala.

- **Una sola clase, agrupada por tema.** `Constantes` contiene clases anidadas: `Rutas`,
  `Paginacion`, `Validacion`, `Texto`, `Autenticacion`, `Mensajes`, `ProblemDetails`, `Grpc`,
  `WebSocket`, `Cors` y `Propiedades`. Se importa el grupo (`import
  com.arquetipo.demo.common.Constantes.Rutas;`) y se usa como `Rutas.REGISTRO`. Si un literal
  nuevo no encaja en ningún grupo, crea uno nuevo en la misma clase, no un archivo aparte.
- **El nombre dice para qué sirve el valor, no cuál es.** Nunca `ZERO = 0`, `UNO = 1` o
  `VACIO = ""`: son `INDICE_PRIMER_CARACTER`, `LONGITUD_UN_CARACTER`, `PUERTO_NO_CONFIGURADO`,
  `CURSOR_PRIMERA_PAGINA`... Dos constantes pueden tener el mismo valor y nombres distintos
  si significan cosas distintas (p. ej. `Grpc.METADATA_USUARIO` y `WebSocket.ATRIBUTO_USUARIO`
  valen los dos `"usuario"`, pero son una cabecera gRPC y un atributo de sesión): cambiar una no
  debe arrastrar a la otra.
- **Valores derivados, compuestos a partir de otros.** Si un valor se deriva de otro, se arma
  desde él en vez de repetirlo (`Rutas.REGISTRO = API_V1 + "/registro"`,
  `Validacion.USERNAME_PATRON_CON_LONGITUD` a partir de las longitudes mínima y máxima del
  username), para que no puedan desincronizarse.
- **También dentro de anotaciones.** `@RequestMapping`, `@RequestParam(defaultValue)`,
  `@Size`, `@Pattern`, `@Value`, `@ConfigurationProperties`... aceptan constantes de compilación
  (`static final` de tipo primitivo o `String`); úsalas también ahí.

**Fuera de alcance, a propósito:**

- Los **mensajes de log**: se leen solo en el `log.*` donde están, y moverlos haría ilegible la
  traza sin ganar reutilización. Esto incluye `RegistroRequest#toString()`, que existe solo para
  enmascarar la contraseña en los logs.
- Los **textos de documentación OpenAPI** (descripciones, resúmenes y ejemplos JSON de
  `@Operation`/`@Schema`/`@ApiResponse`/`@ExampleObject`). Sí se usan constantes en los atributos
  de `@Schema` que repiten una regla de validación (`minLength`/`maxLength`), para que la
  documentación no se desincronice de la regla real.
- Las **directivas del compilador** (`@SuppressWarnings("unchecked")`).
- Los **tests**: conservan sus literales a propósito. Un test que compara contra
  `"/api/v1/registro"` escrito a mano detecta si alguien cambia la constante por error; uno que
  usara la misma constante pasaría igual.

### Paquete `web`: REST y WebSocket por separado

Cuando una feature expone REST **y** WebSocket (hoy solo `conversacion/`), su `web/` se parte en
`web/rest/` (controllers y su `*Api` de OpenAPI) y `web/websocket/` (config, handler e
interceptor del handshake). `web/dto/` queda compartido, porque ambos lados usan los mismos
DTO (`MensajeResponse` sale por el WebSocket y por el historial REST). Las features que solo
exponen REST (`registro/`, `notificacion/`) mantienen `web/` plano.

### DTO: `request/` y `response/` separados

Dentro de `web/dto/` de cada feature, los DTO viven en dos subpaquetes según su dirección:
`request/` para lo que entra (cuerpos de petición REST y el payload del WebSocket, como
`MensajeEntrante`) y `response/` para lo que sale (respuestas y envoltorios como `PageResponse`
o `CursorPage`). Un DTO nuevo se crea directamente en el que le corresponde, nunca suelto en
`dto/`. Un envoltorio genérico que cada feature mantiene por su cuenta (`PageResponse`) se
duplica en el `response/` de cada una, igual que antes.

### Validaciones de acceso fuera del controller

El controller no contiene lógica de validación de token, uid o username: la delega en
`common/auth/ValidadorAcceso`, que expone un método por cada tipo de comprobación
(`autenticar`, `uidCoincide`, `usernameCoincide`). El controller llama al que necesita y solo
mira el resultado: si devuelve `false`, lanza `ForbiddenException` con el mensaje de ese
endpoint (`Mensajes.SIN_PERMISO_*`); un token ausente o inválido ya lo corta el validador con
`UnauthorizedException` (401). Una comprobación nueva (otro recurso, otra forma de comparar) es
un método nuevo en `ValidadorAcceso` con su test, nunca un `if` escrito en el controller.

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
