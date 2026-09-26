# Contratos de API — chat-gateway

Referencia de **todo** lo que expone **chat-gateway** al cliente (frontend web, app móvil):
registro de usuarios, chat en tiempo real (WebSocket + historial + lista de chats + solicitud
de chat) y consultas sobre usuarios (datos propios, disponibilidad de un username). Pensada
para consumirse sin leer el código.

> **Quién atiende cada petición.** Como cliente, hablas siempre con el gateway — por REST o,
> para el chat, por WebSocket — nunca directo con los microservicios. El gateway no implementa
> ninguna regla de negocio: valida el formato de lo que recibe y lo reenvía al microservicio
> correspondiente por **gRPC** (protocolo interno, no expuesto al cliente):
>
> | Endpoint | Microservicio destino | rpc gRPC | Contrato gRPC (para integrar directo, sin el gateway) |
> |---|---|---|---|
> | `POST /api/v1/registro` | `chat-registro` | `RegistroGrpcService/Registrar` (unario) | `chat-registro/docs/contrato-grpc-registro.md` |
> | `GET /api/v1/usuarios/{uid}` | `chat-registro` | `RegistroGrpcService/BuscarUsuarioPorUid` (unario) | ídem |
> | `GET /api/v1/usuarios/existe` | `chat-registro` | `RegistroGrpcService/ExisteUsername` (unario) | ídem |
> | `GET /ws/chat/{usuario}` | `chat-conversacion` | `ConversacionGrpcService/Chat` (bidi streaming) | `chat-conversacion/docs/contrato-grpc-conversacion.md` |
> | `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}` | `chat-conversacion` | `ConversacionGrpcService/Historial` (unario) | ídem |
> | `GET /api/v1/conversaciones/{usuario}/chats` | `chat-conversacion` | `ConversacionGrpcService/ListaChats` (unario) | ídem |
> | `POST /api/v1/conversaciones/solicitudes` | `chat-conversacion` | `ConversacionGrpcService/CrearSolicitud` (unario) | ídem |
>
> El JSON de `POST /api/v1/registro` es exactamente el mismo que documentaba `chat-registro`
> cuando se llamaba directo, y el de `/ws/chat/**`/`/api/v1/conversaciones/**` el mismo que
> documentaba `chat-conversacion` — este cambio de arquitectura no afecta a ningún cliente ya
> integrado, solo cambia el host al que apunta `NEXT_PUBLIC_API_BASE_URL` (o equivalente):
> ahora es el del gateway. `GET /api/v1/conversaciones/{usuario}/chats` y
> `POST /api/v1/conversaciones/solicitudes` son la excepción: ninguno tiene versión "original"
> en `chat-conversacion` (`ListaChats` y `CrearSolicitud` nacieron como rpc gRPC sin
> equivalente REST, ver `chat-conversacion/docs/contrato-grpc-conversacion.md` §5 y §6) — este
> gateway es quien primero los expone por REST.

La fuente de verdad ejecutable del REST es la especificación **OpenAPI** que genera el propio
servicio (el WebSocket no aparece ahí, Swagger no lo documenta); este documento la resume y
añade las notas de integración que no caben en las anotaciones.

- Swagger UI: `http://<host>:8080/swagger-ui.html`
- OpenAPI JSON: `http://<host>:8080/v3/api-docs`

---

## 1. Convenciones generales

| Aspecto | Valor |
|---|---|
| Prefijo de versión (REST) | `/api/v1` (un cambio incompatible sube a `/api/v2`) |
| Formato de cuerpo | JSON (`application/json`) en peticiones y respuestas correctas |
| Formato de errores (REST) | `application/problem+json` (RFC 9457) — el WebSocket no lo usa, ver §3 |
| Codificación | UTF-8 |
| Fechas y horas | ISO-8601 en UTC, con precisión de microsegundos — ej. `2026-09-09T03:13:36.766818Z` |
| Autenticación | Ninguna en `POST /api/v1/registro`, `GET /ws/chat/**` ni `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`. Los demás exigen `Authorization: Bearer <idToken>`: `GET /api/v1/usuarios/{uid}` (§4.2), `GET /api/v1/conversaciones/{usuario}/chats` (§4.5) y `POST /api/v1/conversaciones/solicitudes` (§4.7) comparan la identidad contra el recurso; `GET /api/v1/usuarios/existe` (§4.6) solo exige estar autenticado, sin comparar. |
| CORS (endpoints REST) | Habilitado para `/api/**` en el propio gateway (no en cada microservicio). Orígenes permitidos vía `CORS_ALLOWED_ORIGINS` (lista separada por comas). |
| Orígenes permitidos (WebSocket) | `WEBSOCKET_ALLOWED_ORIGINS` (lista separada por comas). Variable **independiente** de `CORS_ALLOWED_ORIGINS`: el *handshake* de WebSocket no pasa por CORS. |

### Entornos

| Entorno | Base URL | WebSocket |
|---|---|---|
| Local | `http://localhost:8080` | `ws://localhost:8080` |
| Otros | definidos por infraestructura (el gateway escucha en el puerto `8080`) | `ws://` o `wss://` según haya TLS delante |

---

## 2. Formato de errores REST (RFC 9457 *Problem Details*)

Toda respuesta con código `4xx` o `5xx` de un endpoint REST tiene
`Content-Type: application/problem+json` y este cuerpo:

```json
{
  "type": "urn:problem-type:validation-error",
  "title": "Datos invalidos",
  "status": 400,
  "detail": "El cuerpo de la peticion no supero la validacion",
  "instance": "/api/v1/registro",
  "timestamp": "2026-09-09T03:10:00.123456Z"
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `type` | string (URI) | Identificador estable de la categoría de error. **Es el campo que el cliente debe usar para ramificar lógica**, no `title` ni `detail`. |
| `title` | string | Título legible, fijo por `type`. |
| `status` | number | Código HTTP, repetido en el cuerpo. |
| `detail` | string | Descripción legible para mostrar al usuario. Genérica a propósito donde aplica (ver cada endpoint). |
| `instance` | string | Path de la petición que falló. |
| `timestamp` | string (ISO-8601) | Momento en que se generó el error. |
| `errors` | array | **Solo en `validation-error`.** Lista de errores por campo. |

### Catálogo de `type`

| `type` | HTTP | Cuándo | Endpoints donde aplica |
|---|---|---|---|
| `urn:problem-type:validation-error` | 400 | El cuerpo (o, en `/chats`/`/existe`, el parámetro correspondiente) no cumple las reglas de formato. Incluye `errors[]`, vacío si el error no es de un campo concreto. | `POST /api/v1/registro`, `GET /api/v1/conversaciones/{usuario}/chats` (`cursor`), `GET /api/v1/usuarios/existe` (`username`), `POST /api/v1/conversaciones/solicitudes` |
| `urn:problem-type:duplicate-resource` | 409 | Los datos entran en conflicto con un recurso existente. | `POST /api/v1/registro`, `POST /api/v1/conversaciones/solicitudes` (ya existe una solicitud entre ambos usuarios) |
| `urn:problem-type:unauthorized` | 401 | Falta la cabecera `Authorization`, o el `idToken` es inválido/expirado. | `GET /api/v1/usuarios/{uid}`, `GET /api/v1/conversaciones/{usuario}/chats`, `GET /api/v1/usuarios/existe`, `POST /api/v1/conversaciones/solicitudes` |
| `urn:problem-type:forbidden` | 403 | El `idToken` es válido pero de un uid (o del `username` que resuelve ese uid) distinto al pedido. | `GET /api/v1/usuarios/{uid}`, `GET /api/v1/conversaciones/{usuario}/chats`, `POST /api/v1/conversaciones/solicitudes` |
| `urn:problem-type:resource-not-found` | 404 | El recurso solicitado no existe. | `GET /api/v1/usuarios/{uid}`, `GET /api/v1/conversaciones/{usuario}/chats` (sin perfil en `chat-registro` para el uid autenticado), `POST /api/v1/conversaciones/solicitudes` (`solicitante`/`solicitado` no existen) |
| `urn:problem-type:service-unavailable` | 503 | El microservicio destino no está disponible. **Propio del gateway**: el microservicio en solitario nunca lo devuelve. | Todos los REST |
| `urn:problem-type:internal-error` | 500 | Error inesperado. `detail` siempre genérico; el detalle real queda en logs del servidor. | Todos los REST |

---

## 3. El WebSocket no usa Problem Details

`GET /ws/chat/{usuario}` (§4.3) es el único endpoint que no es REST: un mensaje inválido se
descarta en silencio (sin *frame* de error) y un fallo de conexión con el microservicio cierra
la sesión — ver el detalle en §4.3.

---

## 4. Endpoints

### 4.1 `POST /api/v1/registro` — Registrar un usuario

Registra un usuario nuevo. El gateway valida el formato del cuerpo y reenvía la petición por
gRPC a `chat-registro`, que es quien crea la cuenta en el proveedor de identidad (Firebase
Auth) y persiste el perfil.

#### Petición

| | |
|---|---|
| Método | `POST` |
| Path | `/api/v1/registro` |
| Headers | `Content-Type: application/json` |
| Autenticación | Ninguna |

Cuerpo:

```json
{
  "username": "mateo",
  "email": "mateo@example.com",
  "password": "Passw0rd!23"
}
```

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `username` | string | sí | 3–50 caracteres. Solo `A–Z a–z 0–9 . _ -`. Único (sin distinguir mayúsculas). |
| `email` | string | sí | Formato de email válido. Máx. 255 caracteres. Único (sin distinguir mayúsculas). Se normaliza a minúsculas antes de guardar. |
| `password` | string | sí | 8–20 caracteres. Al menos una mayúscula, una minúscula, un número y un carácter especial (cualquiera que no sea letra, número o espacio). Ningún carácter repetido 4 o más veces seguidas (`aaaa` invalido, `aaa` válido). El gateway solo la transporta: no la persiste ni la loguea en ningún punto. |

Se ignora cualquier campo extra del cuerpo (p. ej. un `uid` o `proveedor`: ninguno de los dos
es un campo de la petición — el servidor los determina él mismo).

#### Respuesta `201 Created`

```json
{
  "id": 1,
  "username": "mateo",
  "email": "mateo@example.com",
  "proveedor": "password",
  "activo": true,
  "createdAt": "2026-09-09T03:13:36.766818Z"
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | number | Identificador asignado por el servidor. |
| `username` | string | Tal cual se envió (recortando espacios). |
| `email` | string | Normalizado a minúsculas. |
| `proveedor` | string | Proveedor de identidad usado en el alta (hoy siempre `"password"`). |
| `activo` | boolean | Siempre `true` en un alta nueva. |
| `createdAt` | string (ISO-8601) | Instante de creación en UTC. |

#### Respuesta `400 Bad Request` — validación

`type` = `urn:problem-type:validation-error`. Añade `errors[]` con un objeto por cada campo
que falló:

```json
{
  "type": "urn:problem-type:validation-error",
  "title": "Datos invalidos",
  "status": 400,
  "detail": "El cuerpo de la peticion no supero la validacion",
  "instance": "/api/v1/registro",
  "timestamp": "2026-09-09T03:10:00.123456Z",
  "errors": [
    { "field": "username", "message": "el tamaño debe estar entre 3 y 50" },
    { "field": "email", "message": "debe ser una dirección de correo electrónico con formato correcto" },
    { "field": "password", "message": "debe tener mayuscula, minuscula, numero y caracter especial, y ningun caracter repetido 4 o mas veces seguidas" }
  ]
}
```

`errors[].field` es el nombre del campo del cuerpo; `errors[].message` es orientativo (texto
por defecto de Bean Validation). El gateway aplica estas reglas **antes** de llamar por gRPC —
en el caso normal, este 400 nunca llega a `chat-registro`.

#### Respuesta `409 Conflict` — el usuario ya existe

`type` = `urn:problem-type:duplicate-resource`. **Por seguridad, siempre el mismo mensaje
genérico**, sin indicar qué campo colisionó:

```json
{
  "type": "urn:problem-type:duplicate-resource",
  "title": "Recurso duplicado",
  "status": 409,
  "detail": "No se pudo completar el registro con los datos proporcionados",
  "instance": "/api/v1/registro",
  "timestamp": "2026-09-09T03:10:00.123456Z"
}
```

#### Respuesta `503` / `500`

`503` (`service-unavailable`) si el gateway no pudo alcanzar `chat-registro` por gRPC;
`500` (`internal-error`) ante cualquier otro fallo no cubierto arriba. Ambos reintentables con
backoff.

#### Ejemplo `curl`

```bash
curl -i -X POST http://localhost:8080/api/v1/registro \
  -H 'Content-Type: application/json' \
  -d '{"username":"mateo","email":"mateo@example.com","password":"Passw0rd!23"}'
```

---

### 4.2 `GET /api/v1/usuarios/{uid}` — Datos básicos de un usuario (autenticado)

**El primer endpoint del gateway que exigió autenticación** (ver también §4.5 y §4.6).
Devuelve `username`/`email` de la cuenta con ese `uid` de Firebase, solo si quien pregunta
demuestra ser su dueño.

> **Quién verifica qué.** El gateway valida el `idToken` **él mismo**, con su propia
> integración con Firebase Admin SDK (`common.auth`) — es el único punto del sistema que lo
> hace. Comprueba que el token es válido y que el uid que decodifica coincide con el `{uid}`
> pedido; a `chat-registro` solo le llega el `uid` ya autenticado, **nunca el token**. Sin esa
> comprobación, cualquiera que conociera (o adivinara) un uid ajeno podría leer sus datos con
> solo mandarlo.

#### Petición

| | |
|---|---|
| Método | `GET` |
| Path | `/api/v1/usuarios/{uid}` |
| Path param | `uid` — el UID de Firebase del usuario a consultar (**no** es el `username`) |
| Headers | `Authorization: Bearer <idToken>` — el token de ID de Firebase de quien pregunta |

`idToken` es el mismo token que el cliente obtiene al iniciar sesión con el SDK de Firebase
(`currentUser.getIdToken()`).

> Sin la cabecera `Authorization`, o sin el prefijo `Bearer `, el gateway responde `401`
> **sin llamar por gRPC** — `chat-registro` nunca llega a intentar verificar nada.

#### Respuesta `200 OK`

```json
{
  "username": "mateo",
  "email": "mateo@example.com"
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `username` | string | El `username` con el que se registró. |
| `email` | string | El `email` (normalizado a minúsculas) con el que se registró. |

> No expone nada más del perfil (ni `id`, ni `proveedor`, ni `createdAt`, ni el propio `uid` —
> el cliente ya lo tiene, es el dato de entrada).

#### Respuesta `401 Unauthorized`

Dos causas, misma respuesta:

```json
{
  "type": "urn:problem-type:unauthorized",
  "title": "No autenticado",
  "status": 401,
  "detail": "Falta la cabecera Authorization: Bearer <idToken>",
  "instance": "/api/v1/usuarios/0lSUQS1RdYauzu3ifx6izoyzkvt2",
  "timestamp": "2026-09-19T20:53:47.441193Z"
}
```

- Cabecera `Authorization` ausente, vacía o sin el prefijo `Bearer ` (el gateway la detecta él
  mismo, sin llamar a `chat-registro`).
- El `idToken` tiene forma de `Bearer <algo>` pero `chat-registro` lo rechaza por inválido,
  expirado o revocado (`detail` en ese caso: `"Token de identidad invalido o expirado"`).

#### Respuesta `403 Forbidden`

El `idToken` es válido (pertenece a una sesión real de Firebase), pero decodifica un uid
**distinto** al `uid` pedido en la URL — quien pregunta no es el dueño de esa cuenta:

```json
{
  "type": "urn:problem-type:forbidden",
  "title": "Acceso denegado",
  "status": 403,
  "detail": "El token no autoriza a consultar este usuario",
  "instance": "/api/v1/usuarios/0lSUQS1RdYauzu3ifx6izoyzkvt2",
  "timestamp": "2026-09-19T20:53:47.441193Z"
}
```

#### Respuesta `404 Not Found`

Ningún usuario registrado con ese `uid` (y el `idToken` sí era válido y coincidía — si no, la
respuesta habría sido `401`/`403` antes de llegar a buscar nada).

#### Respuesta `503` / `500`

Mismo criterio que el resto del gateway: `503` si `chat-registro` no responde, `500` ante
cualquier otro fallo inesperado.

#### Ejemplo `curl`

```bash
curl -H "Authorization: Bearer <idToken>" \
  http://localhost:8080/api/v1/usuarios/0lSUQS1RdYauzu3ifx6izoyzkvt2
```

---

### 4.3 `GET /ws/chat/{usuario}` — Chat en tiempo real (WebSocket)

```
ws://localhost:8080/ws/chat/mateo
```

Mismo path que expone `chat-conversacion` directamente — un cliente ya integrado solo cambia
el host (`8080` del gateway en vez de `8082` de `chat-conversacion`). Al conectar, el gateway
abre un stream `ConversacionGrpcService/Chat` hacia `chat-conversacion`, identificado con la
misma cabecera de metadata `usuario`, y traduce cada frame en los dos sentidos mientras la
sesión siga abierta.

`{usuario}` identifica la sesión y es el **`username` de `chat-registro`**: 3–50 caracteres,
`A–Z a–z 0–9 . _ -`. El gateway valida este formato en el propio *handshake* (antes de abrir
ningún stream de gRPC) y rechaza la conexión con `400` si no cumple — la conexión ni se abre.

> ⚠️ **El formato se valida; la identidad no.** Nada comprueba todavía que quien se conecta
> con un `{usuario}` sea el dueño real de esa cuenta. **No usar con datos reales hasta que esto
> se resuelva** (ver `CLAUDE.md`).

#### Mandar un mensaje (cliente → servidor)

Un *frame* de texto con este JSON:

```json
{
  "destinatario": "ana",
  "contenido": "Hola!"
}
```

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `destinatario` | string | sí | Username de chat-registro: 3–50 caracteres, `A–Z a–z 0–9 . _ -`. |
| `contenido` | string | sí | No vacío, máx. 2000 caracteres. |

`remitente` **no** va en este mensaje: lo pone el gateway a partir del `{usuario}` de la
conexión.

> ⚠️ **Un mensaje inválido se descarta en silencio**, tanto si falla el formato (el gateway lo
> corta antes de llegar a gRPC) como si `chat-conversacion` lo rechazara por su cuenta. No hay
> *frame* de error — valida en el cliente antes de mandar.

#### Recibir un mensaje (servidor → cliente)

Mismo JSON en ambas direcciones:

```json
{
  "id": "66f1c2a8b4c9a12345678901",
  "remitente": "mateo",
  "destinatario": "ana",
  "contenido": "Hola!",
  "enviadoEn": "2026-09-18T20:53:47.441193Z"
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | string | `ObjectId` de MongoDB en texto (lo asigna `chat-conversacion`). |
| `remitente` | string | Quien lo mandó. |
| `destinatario` | string | Quien lo recibe. |
| `contenido` | string | Texto del mensaje. |
| `enviadoEn` | string (ISO-8601) | Instante en que `chat-conversacion` lo persistió (UTC). |

Reglas de entrega — idénticas a conectarse directo a `chat-conversacion`, porque es
literalmente el mismo `NotificadorTiempoReal` el que decide la entrega por debajo:

- El remitente recibe de vuelta su propio mensaje ya con `id`/`enviadoEn` — es la única
  confirmación de envío, no hay un *frame* aparte.
- **La entrega cruza protocolos y cruza el gateway**: si el destinatario está conectado
  directo a `chat-conversacion` por su WebSocket o su gRPC (en vez de a través del gateway),
  le llega igual — y viceversa.
- Si el destinatario no tiene ninguna conexión abierta, no recibe nada en tiempo real, pero el
  mensaje queda persistido (disponible por el historial, §4.4).
- Si el gateway pierde la conexión gRPC con `chat-conversacion` mientras el WebSocket sigue
  abierto, la sesión se cierra (código de cierre `1011`, error del servidor) — el cliente debe
  tratar eso como una desconexión y reintentar, igual que si `chat-conversacion` mismo hubiera
  caído.

---

### 4.4 `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}` — Historial de chat

Historial de una conversación entre dos usuarios. El orden de `usuarioA`/`usuarioB` en la URL
no importa. Enrutado por una llamada unaria `ConversacionGrpcService/Historial`.

#### Petición

| | |
|---|---|
| Método | `GET` |
| Path | `/api/v1/conversaciones/{usuarioA}/{usuarioB}` |
| Query params | `page` (0-indexada, por defecto `0`), `size` (por defecto `20`, máx. `100`), `sort` (por defecto `enviadoEn,asc`, formato `"campo,direccion"`) |
| Autenticación | Ninguna |

> A diferencia del WebSocket, aquí `usuarioA`/`usuarioB` no se validan en formato — un valor
> que no exista simplemente no encuentra mensajes. **Solo se admite un criterio de orden por
> petición** (a diferencia del REST original de `chat-conversacion`, que admite `sort`
> repetido): el contrato gRPC que usa el gateway por debajo solo transporta un `sort` — ver
> `chat-conversacion/docs/contrato-grpc-conversacion.md` §4.

#### Respuesta `200 OK`

```json
{
  "content": [
    {
      "id": "66f1c2a8b4c9a12345678901",
      "remitente": "mateo",
      "destinatario": "ana",
      "contenido": "Hola!",
      "enviadoEn": "2026-09-15T20:53:47.441193Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "empty": false
}
```

Sin mensajes entre ambos usuarios: `200` con `content: []`, `totalElements: 0` — nunca `404`.

#### Ejemplo `curl`

```bash
curl "http://localhost:8080/api/v1/conversaciones/mateo/ana?page=0&size=20&sort=enviadoEn,desc"
```

---

### 4.5 `GET /api/v1/conversaciones/{usuario}/chats` — Lista de chats de un usuario (autenticado)

**Segundo endpoint del gateway que exige autenticación** (el primero es §4.2). Un resumen por
cada persona con la que `usuario` tiene al menos un mensaje (en cualquiera de los dos
sentidos), con el último mensaje de esa conversación, ordenados por fecha de ese último
mensaje (más reciente primero). Enrutado por una llamada unaria
`ConversacionGrpcService/ListaChats`. **No existía antes de este gateway**: nació directo
como rpc gRPC en `chat-conversacion` (sin equivalente REST ni WebSocket), y este es el primer
lugar donde se expone por REST.

> Convive con `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}` (§4.4, sin autenticación) sin
> ambigüedad de rutas: el segmento final `chats` es literal, y Spring prioriza un segmento
> literal sobre uno con variable (`{usuarioB}`) al resolver una petición concreta.

> **Quién verifica qué.** Mismo mecanismo que §4.2: el gateway valida el `idToken` **él
> mismo** (`common.auth`, Firebase Admin SDK) — a `chat-conversacion` nunca le llega el token.
> La diferencia es que aquí el recurso lo identifica un `username` de `chat-registro`, no un
> uid de Firebase: el gateway resuelve el `username` del uid autenticado llamando a
> `chat-registro` (`RegistroGrpcService/BuscarUsuarioPorUid`, la misma consulta que usa §4.2) y
> compara ese `username` contra `{usuario}` — un token válido de otro usuario no autoriza a
> leer esta lista.

#### Petición

| | |
|---|---|
| Método | `GET` |
| Path | `/api/v1/conversaciones/{usuario}/chats` |
| Path param | `usuario` — el `username` de `chat-registro` cuyos chats se piden (**no** es el uid de Firebase) |
| Headers | `Authorization: Bearer <idToken>` — el token de ID de Firebase de quien pregunta |
| Query params | `cursor` (opcional; el `nextCursor` de una página anterior, vacío = primera página), `size` (por defecto `20`, máx. `100`) |

> **Paginado por cursor, no por página/offset** — a propósito, porque el orden de la lista
> cambia con cada mensaje nuevo (un offset se desincroniza). Manda `cursor` tal cual llegó en
> `nextCursor`, sin parsearlo ni construirlo a mano: es opaco. Ver
> `chat-conversacion/docs/contrato-grpc-conversacion.md` §5.1 para el porqué.

#### Respuesta `200 OK`

```json
{
  "content": [
    {
      "otroUsuario": "ana",
      "ultimoMensaje": {
        "id": "66f1c2a8b4c9a12345678901",
        "remitente": "mateo",
        "destinatario": "ana",
        "contenido": "Hola!",
        "enviadoEn": "2026-09-15T20:53:47.441193Z"
      }
    }
  ],
  "nextCursor": "MjAyNi0wOS0xNVQyMDo1Mzo0Ny40NDExOTNafDY2ZjFjMmE4YjRjOWExMjM0NTY3ODkwMQ",
  "hasMore": false
}
```

Sin chats: `200` con `content: []`, `hasMore: false` — nunca `404`. **`nextCursor` viene
vacío cuando `hasMore` es `false`**: no lo mandes de vuelta en ese caso, no hay garantía de
que siga siendo válido.

#### Respuesta `400 Bad Request` — cursor inválido

Un `cursor` que no viene de un `nextCursor` real de `chat-conversacion` (formato corrupto o
inventado) responde `400` con `type: urn:problem-type:validation-error` y `errors: []` (el
error no es de un campo del cuerpo, es del propio parámetro `cursor` — `detail` trae el
mensaje real en este caso concreto).

#### Respuesta `401 Unauthorized`

Misma causa y misma forma que §4.2: falta la cabecera `Authorization`, viene sin el prefijo
`Bearer `, o el `idToken` es inválido/expirado/revocado. El gateway responde **sin llamar por
gRPC** a ningún microservicio.

#### Respuesta `403 Forbidden`

El `idToken` es válido, pero el `username` que resuelve ese uid en `chat-registro` es
**distinto** al `usuario` pedido en la URL:

```json
{
  "type": "urn:problem-type:forbidden",
  "title": "Acceso denegado",
  "status": 403,
  "detail": "El token no autoriza a consultar los chats de este usuario",
  "instance": "/api/v1/conversaciones/mateo/chats",
  "timestamp": "2026-09-21T20:53:47.441193Z"
}
```

#### Respuesta `404 Not Found`

El `idToken` es válido pero **no existe ningún perfil en `chat-registro` para ese uid**
todavía (cuenta creada en Firebase, alta en `chat-registro` pendiente) — igual que puede pasar
en §4.2.

#### Respuesta `503` / `500`

Mismo criterio que el resto del gateway: `503` si `chat-registro` (al resolver el `username`)
o `chat-conversacion` (al listar los chats) no responden; `500` ante cualquier otro fallo
inesperado.

#### Ejemplo `curl`

```bash
curl -H "Authorization: Bearer <idToken>" \
  "http://localhost:8080/api/v1/conversaciones/mateo/chats?size=20"
```

---

### 4.6 `GET /api/v1/usuarios/existe` — Comprobar si un username ya está en uso (autenticado)

**Tercer endpoint del gateway que exige autenticación** (los otros dos son §4.2 y §4.5), pero
el único que **no** compara la identidad autenticada contra el recurso pedido: basta con estar
autenticado, sin importar de quién sea el `username` que se consulta — pensado para que un
usuario compruebe si otro existe antes de iniciar un chat con él (o para validación en vivo en
un formulario), no para leer datos propios.

> **Quién verifica qué.** El gateway exige y verifica el `idToken` **él mismo**, igual que en
> §4.2 y §4.5 — pero aquí no hay comparación de por medio: cualquier `idToken` válido basta.
> `chat-registro` tampoco lo necesitaría (esta consulta es pública incluso por gRPC directo,
> ver `chat-registro/docs/contrato-grpc-registro.md` §1) — la exigencia de autenticación es
> una decisión propia del gateway, no heredada de `chat-registro`.

#### Petición

| | |
|---|---|
| Método | `GET` |
| Path | `/api/v1/usuarios/existe` |
| Query param | `username` (**obligatorio**) — el `username` de `chat-registro` a comprobar |
| Headers | `Authorization: Bearer <idToken>` — el token de ID de Firebase de quien pregunta (de cualquier cuenta, no hace falta que sea la del `username` consultado) |

> Convive con `GET /api/v1/usuarios/{uid}` (§4.2) sin ambigüedad de rutas: el segmento
> `existe` es literal y Spring lo prioriza sobre el patrón con variable `{uid}` al resolver una
> petición concreta — mismo mecanismo ya usado entre §4.4 y §4.5.

> No distingue mayúsculas de minúsculas (`Mateo` y `mateo` son el mismo `username` para esta
> comprobación), igual que la unicidad que aplica `POST /api/v1/registro` (§4.1).

#### Respuesta `200 OK`

```json
{
  "existe": true
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `existe` | boolean | `true` si ya hay una cuenta con ese `username`. |

Nunca `404`: un `username` libre es una respuesta válida (`existe: false`), no un error.

#### Respuesta `400 Bad Request`

`username` ausente o vacío:

```json
{
  "type": "urn:problem-type:validation-error",
  "title": "Datos invalidos",
  "status": 400,
  "detail": "El cuerpo de la peticion no supero la validacion",
  "instance": "/api/v1/usuarios/existe",
  "timestamp": "2026-09-22T20:53:47.441193Z",
  "errors": []
}
```

`errors` viene vacío a propósito: el error no es de un campo del cuerpo (este endpoint no
tiene cuerpo), es del propio parámetro `username` — `detail` en ese caso trae el mensaje real
(`chat-registro` responde `"username es obligatorio"`).

#### Respuesta `401 Unauthorized`

Misma causa y misma forma que §4.2: falta la cabecera `Authorization`, viene sin el prefijo
`Bearer `, o el `idToken` es inválido/expirado/revocado. El gateway responde **sin llamar por
gRPC** — a diferencia de §4.2/§4.5, aquí no hay un `403` posible: no existe combinación de
`idToken` válido que este endpoint rechace por identidad.

#### Respuesta `503` / `500`

Mismo criterio que el resto del gateway: `503` si `chat-registro` no responde; `500` ante
cualquier otro fallo inesperado.

#### Ejemplo `curl`

```bash
curl -H "Authorization: Bearer <idToken>" \
  "http://localhost:8080/api/v1/usuarios/existe?username=mateo"
```

---

### 4.7 `POST /api/v1/conversaciones/solicitudes` — Crear una solicitud de chat (autenticado)

**Cuarto endpoint del gateway que exige autenticación** (los otros tres son §4.2, §4.5 y
§4.6), y el segundo que compara identidad (como §4.2 y §4.5, a diferencia de §4.6). Paso
previo obligatorio antes de poder chatear con alguien: enruta por una llamada unaria
`ConversacionGrpcService/CrearSolicitud`. **No existía antes de este gateway**: nació directo
como rpc gRPC en `chat-conversacion` (sin equivalente REST), y este es el primer lugar donde
se expone por REST.

> **Quién verifica qué.** Mismo mecanismo que §4.5: el gateway valida el `idToken` **él
> mismo** y resuelve el `username` del uid autenticado contra `chat-registro`
> (`RegistroService.obtenerUsuario`) para compararlo — pero aquí contra `solicitante` del
> cuerpo, no contra un parámetro de la URL. Un token válido de otro usuario no autoriza a crear
> la solicitud: nadie puede solicitar chatear con alguien **en nombre de** un tercero.

#### Petición

| | |
|---|---|
| Método | `POST` |
| Path | `/api/v1/conversaciones/solicitudes` |
| Headers | `Content-Type: application/json`, `Authorization: Bearer <idToken>` |

Cuerpo:

```json
{
  "solicitante": "mateo",
  "solicitado": "ana"
}
```

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `solicitante` | string | sí | Username (`chat-registro`) de quien inicia la solicitud. 3–50 caracteres, solo `A–Z a–z 0–9 . _ -`. **Debe ser el usuario autenticado** — ver arriba. |
| `solicitado` | string | sí | Username (`chat-registro`) de quien la recibe. Mismo formato. Distinto de `solicitante`. |

#### Respuesta `201 Created`

```json
{
  "id": "66f1c2a8b4c9a12345678901",
  "solicitante": "mateo",
  "solicitado": "ana",
  "aceptada": false,
  "creadaEn": "2026-09-23T20:53:47.441193Z",
  "pendiente": true
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | string | Identificador generado. |
| `solicitante` / `solicitado` | string | Tal cual se enviaron. |
| `aceptada` | boolean | Siempre `false` — aceptar o rechazar una solicitud no está implementado todavía. |
| `creadaEn` | string (ISO-8601) | Instante de creación en UTC. |
| `pendiente` | boolean | Siempre `true` por ahora (nace así y no hay forma de resolverla todavía). Mientras sea `true`, bloquea una solicitud nueva entre el mismo par de usuarios — ver `409` más abajo. |

#### Respuesta `400 Bad Request`

`type` = `urn:problem-type:validation-error`, con `errors[]`. Dos causas:

- `solicitante`/`solicitado` no cumple el formato (mismo criterio que §4.1) — lo detecta el
  gateway, sin llamar por gRPC.
- `solicitante` y `solicitado` son el mismo usuario (`chat-conversacion` lo detecta; `errors`
  viene vacío en este caso, `detail` trae el mensaje real: `"No se puede crear una solicitud
  de chat hacia uno mismo"`).

#### Respuesta `401 Unauthorized` / `403 Forbidden`

Misma causa y forma que §4.2/§4.5: `401` si falta la cabecera, viene sin `Bearer `, o el
`idToken` es inválido/expirado; `403` si el `idToken` es válido pero de un usuario distinto a
`solicitante` — en ambos casos, sin llamar por gRPC.

#### Respuesta `404 Not Found`

`solicitante` o `solicitado` no existen en `chat-registro` (`detail`:
`"No existe el usuario solicitante/solicitado '<username>'"`) — nunca puede ser `solicitante`
si coincidió con el usuario autenticado (ya se sabe que existe), pero sí puede serlo si el
perfil de `chat-registro` se borró entre la autenticación y esta comprobación.

#### Respuesta `409 Conflict`

Ya existe una solicitud **pendiente** (`pendiente: true`) entre `solicitante` y `solicitado`,
en cualquier sentido (`detail`: `"Ya existe una solicitud de chat pendiente entre '<a>' y
'<b>'"`) — en ese caso no se crea nada nuevo. Una solicitud anterior ya resuelta
(`pendiente: false`, aunque hoy no hay forma de llegar a ese estado) no cuenta para este
chequeo y no bloquea una solicitud nueva.

#### Respuesta `503` / `500`

Mismo criterio que el resto del gateway: `503` si `chat-registro` (al resolver el `username`
autenticado, o al validar `solicitante`/`solicitado` dentro de `chat-conversacion`) o
`chat-conversacion` (al crear la solicitud) no responden; `500` ante cualquier otro fallo
inesperado.

> **La notificación por RabbitMQ es asunto de `chat-conversacion`, no del gateway.** Tras
> crear la solicitud, `chat-conversacion` publica un mensaje best-effort en el exchange
> `chat.notificaciones` — un fallo ahí no afecta esta respuesta (ver
> `chat-conversacion/docs/contrato-grpc-conversacion.md` §6.3). El gateway no consume ni
> reenvía esas notificaciones.

#### Ejemplo `curl`

```bash
curl -i -X POST http://localhost:8080/api/v1/conversaciones/solicitudes \
  -H "Authorization: Bearer <idToken>" \
  -H 'Content-Type: application/json' \
  -d '{"solicitante":"mateo","solicitado":"ana"}'
```

---

## 5. Notas de integración para el frontend

1. **Ramifica por `type`, no por `status` ni por textos.** `title`/`detail` pueden cambiar de
   redacción sin previo aviso; `type` y `status` son estables.
2. **El `409` del registro no dice qué campo colisiona.** Es intencional. Mensaje genérico en
   la UI.
3. **La contraseña nunca vuelve en ninguna respuesta.** No la guardes ni la muestres tras el
   alta.
4. **El `email` se normaliza a minúsculas** en el servidor; si tu UI lo muestra tras el alta,
   usa el valor devuelto en la respuesta, no el que tecleó el usuario.
5. **Valida `password` (registro) y `destinatario`/`contenido` (chat) en el cliente** con las
   mismas reglas de §4.1/§4.3 para dar *feedback* inmediato; la validación del servidor es la
   autoritativa.
6. **Unicidad de `username`/`email` no se puede pre-comprobar.** Se descubre al recibir el
   `409` del `POST /api/v1/registro`.
7. **Un `503` es distinto de un `500`.** El primero significa "el servicio destino está caído,
   probablemente reintentable pronto"; el segundo es un fallo inesperado. Trátalos distinto en
   la UI si tu app diferencia "servicio en mantenimiento" de "algo salió mal".
8. **`uid` (Firebase) no es `username` (chat-registro).** Son dos identificadores distintos del
   mismo usuario — `GET /api/v1/usuarios/{uid}` usa el primero, todo lo demás (chat, historial)
   usa el segundo.
9. **El `idToken` de `GET /api/v1/usuarios/{uid}` se obtiene del SDK de Firebase**
   (`currentUser.getIdToken()`). No lo guardes tú mismo en `localStorage` ni en cookies — el
   SDK ya lo cachea y refresca solo cuando hace falta.
10. **Un `403` no significa "el uid no existe"** — significa "tu sesión no es la de ese uid".
    Un `404` sí significa que no hay cuenta con ese uid. No los trates igual en la UI.
11. **El WebSocket no confirma ni rechaza mensajes inválidos.** Valida `destinatario`/`contenido`
    en el cliente antes de mandar (§4.3); no hay *frame* de error.
12. **Carga el historial por REST al abrir la pantalla de chat, y luego conéctate al
    WebSocket** para los mensajes nuevos: son dos canales independientes.
13. **El envío/recepción de mensajes y el historial no tienen autenticación todavía**
    (`GET /ws/chat/**`, `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`) — no trates ese
    flujo como seguro para datos reales hasta que se resuelva (ver `CLAUDE.md`).
    `GET /api/v1/conversaciones/{usuario}/chats` (§4.5) es la excepción: sí exige el mismo
    `idToken` que §4.2.
14. **`GET /api/v1/conversaciones/{usuario}/chats` es la pantalla de "conversaciones", no el
    chat en sí.** Úsalo para listar con quién ha hablado `usuario` y el último mensaje de cada
    uno (p. ej. al abrir la app); abre el WebSocket (§4.3) o pide el historial (§4.4) solo
    cuando el usuario entra a un chat concreto — ninguno de los dos hereda la autenticación de
    §4.5, son llamadas independientes.
15. **El `idToken` de `GET /api/v1/conversaciones/{usuario}/chats` se obtiene igual que en
    §4.2** (`currentUser.getIdToken()` del SDK de Firebase) — no hace falta pedirlo dos veces
    si ya lo tienes de resolver "tu usuario" con `GET /api/v1/usuarios/{uid}`.
16. **`GET /api/v1/usuarios/existe` (§4.6) no es "para consultar tus propios datos"** — es la
    única consulta autenticada que no compara identidad: cualquier sesión válida puede
    preguntar por la disponibilidad de cualquier `username`. No la uses para decidir si "el
    usuario actual" existe (eso ya lo resuelve `GET /api/v1/usuarios/{uid}`, §4.2); úsala para
    preguntar por *otro* usuario, p. ej. antes de iniciar un chat con él.
17. **Flujo típico para iniciar un chat nuevo**: comprueba que el otro usuario existe
    (`GET /api/v1/usuarios/existe`, §4.6) → crea la solicitud
    (`POST /api/v1/conversaciones/solicitudes`, §4.7, con `solicitante` = tu propio `username`)
    → recién entonces abre el WebSocket (§4.3) o carga el historial (§4.4). Saltarse el paso de
    la solicitud no está bloqueado técnicamente por `chat-conversacion` todavía (el WebSocket y
    el historial no la exigen), pero es el flujo pensado.
18. **`solicitante` en `POST /api/v1/conversaciones/solicitudes` siempre debe ser tu propio
    `username`**, nunca el de otro — un valor distinto responde `403` sin llegar a
    `chat-conversacion`. No hay forma de crear una solicitud "en nombre de" otro usuario.
19. **Una solicitud rechazada por `409` no significa que la conversación ya exista** — solo que
    ya hay una solicitud (aceptada o no, no se distingue todavía) entre esos dos usuarios. No
    hay manera de "reintentar" salvo que la solicitud original se elimine (no hay endpoint para
    eso tampoco).

---

## 6. Modelos (TypeScript)

```ts
// --- Registro (§4.1) ---
export interface RegistroRequest {
  username: string; // 3–50, /^[A-Za-z0-9._-]+$/
  email: string;    // email válido, <= 255
  password: string; // 8–20; mayuscula + minuscula + numero + especial; sin 4+ repetidos
}

export interface RegistroResponse {
  id: number;
  username: string;
  email: string;
  proveedor: string; // hoy siempre "password"
  activo: boolean;
  createdAt: string; // ISO-8601 UTC
}

// --- Datos de usuario (§4.2) ---
export interface UsuarioResponse {
  username: string;
  email: string;
}

// --- Disponibilidad de username (§4.6) ---
export interface ExisteUsernameResponse {
  existe: boolean;
}

// --- Chat (§4.3 WebSocket, §4.4 historial) ---
export interface MensajeEntrante {
  destinatario: string; // username de chat-registro: 3-50, /^[A-Za-z0-9._-]+$/
  contenido: string;    // no vacio, <= 2000 caracteres
}

export interface MensajeResponse {
  id: string;
  remitente: string;
  destinatario: string;
  contenido: string;
  enviadoEn: string; // ISO-8601 UTC
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

// --- Lista de chats (§4.5) ---
export interface ChatResumen {
  otroUsuario: string;
  ultimoMensaje: MensajeResponse;
}

export interface CursorPage<T> {
  content: T[];
  nextCursor: string; // vacio si hasMore es false; no lo reenvies en ese caso
  hasMore: boolean;
}

// --- Solicitud de chat (§4.7) ---
export interface SolicitudChatRequest {
  solicitante: string; // debe ser tu propio username; 3-50, /^[A-Za-z0-9._-]+$/
  solicitado: string;  // 3-50, /^[A-Za-z0-9._-]+$/; distinto de solicitante
}

export interface SolicitudChatResponse {
  id: string;
  solicitante: string;
  solicitado: string;
  aceptada: boolean;  // siempre false por ahora
  creadaEn: string;   // ISO-8601 UTC
  pendiente: boolean; // siempre true por ahora; bloquea una solicitud nueva entre el mismo par
}

// --- Error RFC 9457 (cualquier 4xx/5xx de un endpoint REST) ---
export interface ProblemDetail {
  type: string;      // "urn:problem-type:*"
  title: string;
  status: number;
  detail: string;
  instance: string;
  timestamp: string; // ISO-8601 UTC
  errors?: FieldError[]; // solo en validation-error
}

export interface FieldError {
  field: string;   // "username" | "email" | "password"
  message: string; // orientativo
}
```

---

## 7. Cómo funciona por dentro

Dos features, cada una `Controller`/`Handler` → `Service` → `GrpcClient`, sin lógica de
negocio propia (esa vive en el microservicio destino):

- **`registro/`** (`POST /api/v1/registro`, `GET /api/v1/usuarios/{uid}`,
  `GET /api/v1/usuarios/existe`): `RegistroController` valida el cuerpo y delega en
  `RegistroService` → `RegistroGrpcClient`, que traduce a
  `RegistrarUsuarioRequest`/`BuscarUsuarioPorUidRequest`/`ExisteUsernameRequest` e invoca
  `chat-registro`. La orquestación real del alta (creación en el proveedor de identidad,
  reconciliación, compensación) vive en `chat-registro` — ver su
  `docs/contrato-grpc-registro.md`. En cambio, los dos endpoints de `UsuarioController` **sí
  autentican ellos mismos**: ambos llaman a `AutenticacionExtractor` (ver
  `arquitectura-gateway.md`) para verificar el `idToken` con la integración propia del gateway
  con Firebase — a `chat-registro` le llega solo el dato ya autenticado, nunca el token —, pero
  con distinto alcance: `obtenerUsuario` además compara el uid autenticado contra el `{uid}`
  pedido (403 si no coincide) antes de delegar en `RegistroService`; `existeUsername` no
  compara nada, solo exige que la verificación no falle.
- **`conversacion/`** (`GET /ws/chat/{usuario}`, `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`,
  `GET /api/v1/conversaciones/{usuario}/chats`, `POST /api/v1/conversaciones/solicitudes`):
  `ChatWebSocketHandler` abre, al conectarse una sesión, un stream `Chat` de gRPC hacia
  `chat-conversacion` vía `ConversacionService.abrirChat` → `ConversacionGrpcClient` — la
  cabecera de metadata `usuario` la manda el gateway con el mismo `{usuario}` que validó
  `UsuarioHandshakeInterceptor` al conectar. Cada frame de texto entrante se valida y se
  reenvía por el stream; cada `MensajeEntregado` que llega por el stream se traduce a JSON y se
  manda por el socket. El historial, la lista de chats y crear una solicitud siguen el mismo
  patrón unario que el registro: `ConversacionController` → `ConversacionService` →
  `ConversacionGrpcClient` (llamadas `Historial`/`ListaChats`/`CrearSolicitud`) — el `cursor`
  de `ListaChats` viaja tal cual, sin decodificarlo: solo `chat-conversacion` sabe
  interpretarlo. `listaChats` y `crearSolicitud` **sí autentican**, igual que
  `UsuarioController`: llaman a `AutenticacionExtractor` para verificar el `idToken` y, como el
  recurso lo identifica un `username` y no un uid, resuelven ese `username` con
  `RegistroService.obtenerUsuario` (la misma dependencia cruzada `conversacion` → `registro`
  que existe solo para esta comprobación) antes de comparar — contra `usuario` en un caso,
  contra `solicitante` del cuerpo en el otro — y delegar en `ConversacionService`.

Ver también [`arquitectura-gateway.md`](arquitectura-gateway.md) para el patrón completo y
cómo se añade un microservicio nuevo al gateway.

---

## 8. Otros recursos del servicio

| Recurso | Path | Uso |
|---|---|---|
| Swagger UI | `/swagger-ui.html` | Exploración interactiva (solo endpoints REST, no el WebSocket) |
| OpenAPI JSON | `/v3/api-docs` | Generación de clientes / tipos |
| Health check | `/actuator/health` | Monitorización / readiness |

---

## 9. Control de versiones de este documento

| Fecha | Cambio |
|---|---|
| 2026-09-24 (2) | `SolicitudChatResponse` suma el campo `pendiente`. Nueva regla de negocio en `chat-conversacion`: el `409` de §4.7 solo ocurre si ya existe una solicitud **pendiente** entre los dos usuarios — antes bloqueaba cualquier solicitud previa, sin distinguir su estado. |
| 2026-09-24 (1) | Se añade `POST /api/v1/conversaciones/solicitudes`, enrutando por gRPC a `ConversacionGrpcService/CrearSolicitud` (`chat-conversacion`) — paso previo obligatorio para poder chatear con alguien. Exige autenticación y compara identidad: `solicitante` debe ser el `username` del uid autenticado (mismo mecanismo que §4.5), 403 si no coincide. |
| 2026-09-22 | Se añade `GET /api/v1/usuarios/existe`, enrutando por gRPC a `RegistroGrpcService/ExisteUsername` (`chat-registro`). Exige autenticación (cualquier `idToken` válido) pero, a diferencia de §4.2 y §4.5, no compara identidad contra el recurso — cualquier usuario autenticado puede preguntar por la disponibilidad de cualquier `username`. |
| 2026-09-21 | `GET /api/v1/conversaciones/{usuario}/chats` pasa a exigir autenticación (`Authorization: Bearer <idToken>`), mismo mecanismo que §4.2: el gateway resuelve el `username` del uid autenticado contra `chat-registro` y lo compara con `{usuario}` (403 si no coincide) — antes de esto no tenía ningún control de acceso. |
| 2026-09-20 (3) | Se añade `GET /api/v1/conversaciones/{usuario}/chats`, enrutando por gRPC a `ConversacionGrpcService/ListaChats` — primer endpoint REST del gateway sin equivalente previo en `chat-conversacion` (paginado por cursor, no por página/offset). |
| 2026-09-20 (2) | El contrato externo de `GET /api/v1/usuarios/{uid}` no cambia (mismo `401`/`403`/`404`), pero por dentro el gateway pasa a validar el `idToken` **él mismo** (integración propia con Firebase Admin SDK) en vez de reenviarlo a `chat-registro` — es una decisión de arquitectura: el gateway es el único punto del sistema que valida tokens de identidad, para que futuros microservicios con autenticación no necesiten integrarse cada uno con Firebase. Ver `arquitectura-gateway.md`. |
| 2026-09-20 (1) | Se fusionan en este único documento los contratos que antes vivían separados en `contratos-api-conversacion.md` y `contratos-api-usuarios.md` (WebSocket + historial de chat, y consulta autenticada de usuario) — ambos archivos se eliminan, este es ahora el contrato REST/WebSocket completo del gateway. |
| 2026-09-19 | Se añade `GET /api/v1/usuarios/{uid}` (autenticado, `Authorization: Bearer <idToken>`). |
| 2026-09-18 | Se añaden `GET /ws/chat/{usuario}` y `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`, enrutando por gRPC a `chat-conversacion`. |
| 2026-09-13 | `chat-gateway` pasa a ser el punto de entrada REST; el contrato de `POST /api/v1/registro` no cambia para el cliente, pero ahora lo sirve el gateway (reenvío por gRPC a `chat-registro`). Se documenta el `503 service-unavailable`, propio del gateway. |
| (anterior) | Historial de cuando `chat-registro` se llamaba directo: ver `chat-registro/docs/contratos-api.md` §8 (histórico, ese documento ya no existe — `chat-registro` no expone REST). |
