# Contratos de API — conversacion (vía chat-gateway)

Referencia de lo que expone **chat-gateway** para el chat en tiempo real: un canal de
**WebSocket** para mandar/recibir mensajes de texto (1 a 1) y un endpoint **REST** para leer
el historial de una conversación. Mismo contrato JSON que documentaba `chat-conversacion`
cuando se llamaba directo — este documento solo aclara qué hace el gateway por debajo.

> **Quién atiende esta conexión.** El cliente habla con el gateway, nunca directo con
> `chat-conversacion`. Para el WebSocket, el gateway abre — al conectarse el cliente — un
> stream `ConversacionGrpcService/Chat` hacia `chat-conversacion` y traduce cada frame en los
> dos sentidos; para el historial, traduce la petición REST a una llamada unaria
> `ConversacionGrpcService/Historial`. Ver
> [`chat-conversacion/docs/contrato-grpc-conversacion.md`](../../chat-conversacion/docs/contrato-grpc-conversacion.md)
> para el contrato gRPC exacto que usa el gateway por debajo, y
> [`contratos-api.md`](contratos-api.md) para el otro endpoint que expone el gateway
> (registro de usuarios).

- Swagger UI (solo documenta el endpoint REST, no el WebSocket): `http://<host>:8080/swagger-ui.html`
- OpenAPI JSON: `http://<host>:8080/v3/api-docs`

---

## 1. Convenciones generales

| Aspecto | Valor |
|---|---|
| Prefijo de versión (REST) | `/api/v1` |
| Formato de errores (REST) | `application/problem+json` (RFC 9457) |
| Codificación | UTF-8 |
| Fechas y horas | ISO-8601 en UTC, con precisión de microsegundos |
| Autenticación | **Ninguna todavía** — mismo aviso de `chat-conversacion`: el formato de `{usuario}` se valida, la identidad no (ver §2.1). |
| Orígenes permitidos (WebSocket) | `WEBSOCKET_ALLOWED_ORIGINS` (lista separada por comas). Por defecto solo `http://localhost:3000`. Variable **independiente** de `CORS_ALLOWED_ORIGINS` — el handshake de WebSocket no pasa por CORS. |
| CORS (endpoint REST) | Cubierto por el `CorsConfig` global del gateway (`/api/**`), el mismo que usa el registro de usuarios — no hace falta configurarlo aparte para este endpoint. |

### Entornos

| Entorno | Base URL | WebSocket |
|---|---|---|
| Local | `http://localhost:8080` | `ws://localhost:8080` |
| Otros | el host del gateway en ese entorno | `ws://` o `wss://` según haya TLS delante |

---

## 2. WebSocket — chat en tiempo real

### 2.1 Conexión — `GET /ws/chat/{usuario}` (upgrade a WebSocket)

```
ws://localhost:8080/ws/chat/mateo
```

Mismo path que expone `chat-conversacion` directamente — un cliente ya integrado solo cambia
el host (`8080` del gateway en vez de `8082` de `chat-conversacion`).

`{usuario}` identifica la sesión y es el **`username` de `chat-registro`**: 3–50 caracteres,
`A–Z a–z 0–9 . _ -`. El gateway valida este formato en el propio *handshake* (antes de abrir
ningún stream de gRPC) y rechaza la conexión con `400` si no cumple — la conexión ni se abre.

> ⚠️ **El formato se valida; la identidad no.** Igual que en `chat-conversacion`, nada
> comprueba todavía que quien se conecta con un `{usuario}` sea el dueño real de esa cuenta.
> **No usar con datos reales hasta que esto se resuelva** (ver `CLAUDE.md`).

### 2.2 Mandar un mensaje (cliente → servidor)

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
conexión (§2.1).

> ⚠️ **Un mensaje inválido se descarta en silencio**, tanto si falla el formato (el gateway lo
> corta antes de llegar a gRPC) como si `chat-conversacion` lo rechazara por su cuenta. No hay
> *frame* de error — valida en el cliente antes de mandar.

### 2.3 Recibir un mensaje (servidor → cliente)

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
  mensaje queda persistido (disponible por el historial, §3).
- Si el gateway pierde la conexión gRPC con `chat-conversacion` mientras el WebSocket sigue
  abierto, la sesión se cierra (código de cierre `1011`, error del servidor) — el cliente debe
  tratar eso como una desconexión y reintentar, igual que si `chat-conversacion` mismo hubiera
  caído.

---

## 3. REST — `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`

Historial de una conversación entre dos usuarios. El orden de `usuarioA`/`usuarioB` en la URL
no importa.

#### Petición

| | |
|---|---|
| Método | `GET` |
| Path | `/api/v1/conversaciones/{usuarioA}/{usuarioB}` |
| Query params | `page` (0-indexada, por defecto `0`), `size` (por defecto `20`, máx. `100`), `sort` (por defecto `enviadoEn,asc`, formato `"campo,direccion"`) |
| Autenticación | Ninguna (misma advertencia de §2.1) |

> A diferencia del WebSocket, aquí `usuarioA`/`usuarioB` no se validan en formato — un valor
> que no exista simplemente no encuentra mensajes. **Solo se admite un criterio de orden por
> petición** (a diferencia del REST original de `chat-conversacion`, que admite `sort`
> repetido): el contrato gRPC que usa el gateway por debajo solo transporta un `sort` — ver
> `contrato-grpc-conversacion.md` §4.

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

## 4. Errores

### WebSocket (§2)

No usa Problem Details: un mensaje inválido se descarta en silencio (§2.2); un fallo de
conexión con `chat-conversacion` cierra la sesión (§2.3).

### REST (§3)

Mismo formato RFC 9457 que el resto del gateway (ver [`contratos-api.md`](contratos-api.md) §2):

| `type` | HTTP | Cuándo |
|---|---|---|
| `urn:problem-type:service-unavailable` | 503 | `chat-conversacion` no está disponible. |
| `urn:problem-type:internal-error` | 500 | Error inesperado. `detail` genérico. |

---

## 5. Notas de integración para el frontend

1. **No hay autenticación todavía** — no trates este chat como seguro para datos reales hasta
   que se resuelva (ver `CLAUDE.md`).
2. **Valida `destinatario`/`contenido` en el cliente** antes de mandar (§2.2): ni el gateway
   ni `chat-conversacion` devuelven un error explícito por el socket.
3. **El mensaje que llega por el socket es la única confirmación de envío.**
4. **Carga el historial por REST al abrir la pantalla, y luego conéctate al WebSocket** para
   los mensajes nuevos — dos canales independientes.
5. **Un `503` en el historial es distinto de un `500`**: el primero significa que
   `chat-conversacion` está caído (probablemente reintentable pronto).

---

## 6. Modelos (TypeScript)

```ts
// Mensaje que manda el cliente por el WebSocket
export interface MensajeEntrante {
  destinatario: string; // username de chat-registro: 3-50, /^[A-Za-z0-9._-]+$/
  contenido: string;    // no vacio, <= 2000 caracteres
}

// Mensaje persistido: llega por el WebSocket y en el historial REST
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

// Error RFC 9457 (solo en el endpoint REST)
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  timestamp: string;
}
```

---

## 7. Cómo funciona por dentro

`ChatWebSocketHandler` (`com.arquetipo.demo.conversacion.web`) abre, al conectarse una sesión,
un stream `Chat` de gRPC hacia `chat-conversacion` vía `ConversacionService.abrirChat` →
`ConversacionGrpcClient` — la cabecera de metadata `usuario` la manda el gateway con el mismo
`{usuario}` que validó `UsuarioHandshakeInterceptor` al conectar. Cada frame de texto entrante
se valida (Bean Validation, mismas reglas que `chat-conversacion`) y se reenvía por el stream;
cada `MensajeEntregado` que llega por el stream se traduce a JSON y se manda por el socket. El
historial REST sigue el mismo patrón que el registro de usuarios: `ConversacionController` →
`ConversacionService` → `ConversacionGrpcClient` (llamada unaria `Historial`).

Ver también [`arquitectura-gateway.md`](arquitectura-gateway.md).

---

## 8. Control de versiones de este documento

| Fecha | Cambio |
|---|---|
| 2026-09-18 | Versión inicial: el gateway pasa a exponer `/ws/chat/{usuario}` y `GET /api/v1/conversaciones/{usuarioA}/{usuarioB}`, enrutando por gRPC a `chat-conversacion`. |
