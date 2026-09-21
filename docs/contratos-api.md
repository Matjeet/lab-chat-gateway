# Contratos de API — chat-gateway

Referencia de **todo** lo que expone **chat-gateway** al cliente (frontend web, app móvil):
registro de usuarios, chat en tiempo real (WebSocket + historial) y consulta de datos de
usuario. Pensada para consumirse sin leer el código.

> **Quién atiende cada petición.** Como cliente, hablas siempre con el gateway — por REST o,
> para el chat, por WebSocket — nunca directo con los microservicios. El gateway no implementa
> ninguna regla de negocio: valida el formato de lo que recibe y lo reenvía al microservicio
> correspondiente por **gRPC** (protocolo interno, no expuesto al cliente):
>
> | Endpoint | Microservicio destino | rpc gRPC | Contrato gRPC (para integrar directo, sin el gateway) |
> |---|---|---|---|
> | `POST /api/v1/registro` | `chat-registro` | `RegistroGrpcService/Registrar` (unario) | `chat-registro/docs/contrato-grpc-registro.md` |
> | `GET /api/v1/usuarios/{uid}` | `chat-registro` | `RegistroGrpcService/BuscarUsuarioPorUid` (unario) | ídem |
> | `GET /ws/chat/{usuario}` | `chat-conversacion` | `ConversacionGrpcService/Chat` (bidi streaming) | `chat-conversacion/docs/contrato-grpc-conversacion.md` |
> | `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}` | `chat-conversacion` | `ConversacionGrpcService/Historial` (unario) | ídem |
>
> El JSON de `POST /api/v1/registro` es exactamente el mismo que documentaba `chat-registro`
> cuando se llamaba directo, y el de `/ws/chat/**`/`/api/v1/conversaciones/**` el mismo que
> documentaba `chat-conversacion` — este cambio de arquitectura no afecta a ningún cliente ya
> integrado, solo cambia el host al que apunta `NEXT_PUBLIC_API_BASE_URL` (o equivalente):
> ahora es el del gateway.

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
| Autenticación | Ninguna en `POST /api/v1/registro`, `GET /ws/chat/**` ni `GET /api/v1/conversaciones/**`. `GET /api/v1/usuarios/{uid}` es la única excepción — exige `Authorization: Bearer <idToken>`, ver §4.4. |
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
| `urn:problem-type:validation-error` | 400 | El cuerpo no cumple las reglas de formato. Incluye `errors[]`. | `POST /api/v1/registro` |
| `urn:problem-type:duplicate-resource` | 409 | Los datos entran en conflicto con un usuario existente. | `POST /api/v1/registro` |
| `urn:problem-type:unauthorized` | 401 | Falta la cabecera `Authorization`, o el `idToken` es inválido/expirado. | `GET /api/v1/usuarios/{uid}` |
| `urn:problem-type:forbidden` | 403 | El `idToken` es válido pero de un uid distinto al pedido. | `GET /api/v1/usuarios/{uid}` |
| `urn:problem-type:resource-not-found` | 404 | El recurso solicitado no existe. | `GET /api/v1/usuarios/{uid}` |
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

**El único endpoint del gateway que exige autenticación.** Devuelve `username`/`email` de la
cuenta con ese `uid` de Firebase, solo si quien pregunta demuestra ser su dueño.

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
13. **No hay autenticación en el chat todavía** (`GET /ws/chat/**`, `GET /api/v1/conversaciones/**`)
    — no trates ese flujo como seguro para datos reales hasta que se resuelva (ver `CLAUDE.md`).

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

- **`registro/`** (`POST /api/v1/registro`, `GET /api/v1/usuarios/{uid}`): `RegistroController`
  valida el cuerpo y delega en `RegistroService` → `RegistroGrpcClient`, que traduce a
  `RegistrarUsuarioRequest`/`BuscarUsuarioPorUidRequest` e invoca `chat-registro`. La
  orquestación real del alta (creación en el proveedor de identidad, reconciliación,
  compensación) vive en `chat-registro` — ver su `docs/contrato-grpc-registro.md`. En cambio,
  `UsuarioController` **sí autentica él mismo**: llama a `AutenticacionExtractor` (ver
  `arquitectura-gateway.md`) para verificar el `idToken` con la integración propia del gateway
  con Firebase, compara el uid autenticado contra el `{uid}` pedido, y solo entonces delega en
  `RegistroService` — a `chat-registro` le llega el `uid` desnudo, sin ningún token.
- **`conversacion/`** (`GET /ws/chat/{usuario}`, `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`):
  `ChatWebSocketHandler` abre, al conectarse una sesión, un stream `Chat` de gRPC hacia
  `chat-conversacion` vía `ConversacionService.abrirChat` → `ConversacionGrpcClient` — la
  cabecera de metadata `usuario` la manda el gateway con el mismo `{usuario}` que validó
  `UsuarioHandshakeInterceptor` al conectar. Cada frame de texto entrante se valida y se
  reenvía por el stream; cada `MensajeEntregado` que llega por el stream se traduce a JSON y se
  manda por el socket. El historial sigue el mismo patrón unario que el registro:
  `ConversacionController` → `ConversacionService` → `ConversacionGrpcClient` (llamada
  `Historial`).

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
| 2026-09-20 (2) | El contrato externo de `GET /api/v1/usuarios/{uid}` no cambia (mismo `401`/`403`/`404`), pero por dentro el gateway pasa a validar el `idToken` **él mismo** (integración propia con Firebase Admin SDK) en vez de reenviarlo a `chat-registro` — es una decisión de arquitectura: el gateway es el único punto del sistema que valida tokens de identidad, para que futuros microservicios con autenticación no necesiten integrarse cada uno con Firebase. Ver `arquitectura-gateway.md`. |
| 2026-09-20 (1) | Se fusionan en este único documento los contratos que antes vivían separados en `contratos-api-conversacion.md` y `contratos-api-usuarios.md` (WebSocket + historial de chat, y consulta autenticada de usuario) — ambos archivos se eliminan, este es ahora el contrato REST/WebSocket completo del gateway. |
| 2026-09-19 | Se añade `GET /api/v1/usuarios/{uid}` (autenticado, `Authorization: Bearer <idToken>`). |
| 2026-09-18 | Se añaden `GET /ws/chat/{usuario}` y `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`, enrutando por gRPC a `chat-conversacion`. |
| 2026-09-13 | `chat-gateway` pasa a ser el punto de entrada REST; el contrato de `POST /api/v1/registro` no cambia para el cliente, pero ahora lo sirve el gateway (reenvío por gRPC a `chat-registro`). Se documenta el `503 service-unavailable`, propio del gateway. |
| (anterior) | Historial de cuando `chat-registro` se llamaba directo: ver `chat-registro/docs/contratos-api.md` §8 (histórico, ese documento ya no existe — `chat-registro` no expone REST). |
