# Contratos de API — chat-gateway

Referencia de los endpoints HTTP que expone **chat-gateway**, pensada para que un cliente
(frontend web, app móvil) los consuma sin leer el código.

> **Quién atiende esta petición.** Como cliente, hablas siempre con el gateway por REST — es
> el único servicio con el que el cliente tiene contacto directo. El gateway no implementa
> ninguna regla de negocio: valida el formato del cuerpo y reenvía la petición al
> microservicio correspondiente por **gRPC** (protocolo interno, no expuesto al cliente). Para
> el endpoint de abajo, el destino es **`chat-registro`**; su contrato gRPC (para quien quiera
> integrarlo directamente, sin pasar por el gateway) está en
> `chat-registro/docs/contrato-grpc-registro.md`. El JSON que ves aquí es exactamente el mismo
> que documentaba `chat-registro` cuando se llamaba directo — este cambio no afecta a ningún
> cliente ya integrado, solo cambia el host al que apunta `NEXT_PUBLIC_API_BASE_URL` (o
> equivalente): ahora es el del gateway, no el de `chat-registro`.

La fuente de verdad ejecutable es la especificación **OpenAPI** que genera el propio
servicio; este documento la resume y añade las notas de integración que no caben en las
anotaciones.

- Swagger UI: `http://<host>:8080/swagger-ui.html`
- OpenAPI JSON: `http://<host>:8080/v3/api-docs`

---

## 1. Convenciones generales

| Aspecto | Valor |
|---|---|
| Prefijo de versión | `/api/v1` (un cambio incompatible sube a `/api/v2`) |
| Formato de cuerpo | JSON (`application/json`) en peticiones y respuestas correctas |
| Formato de errores | `application/problem+json` (RFC 9457) |
| Codificación | UTF-8 |
| Fechas y horas | ISO-8601 en UTC, con precisión de microsegundos — ej. `2026-09-09T03:13:36.766818Z` |
| Autenticación | Ninguna exigida hoy por `POST /api/v1/registro` (es el propio alta). |
| CORS | Habilitado para `/api/**` en el propio gateway (no en cada microservicio). Orígenes permitidos vía `CORS_ALLOWED_ORIGINS` (lista separada por comas). |

### Entornos

| Entorno | Base URL |
|---|---|
| Local | `http://localhost:8080` |
| Otros | definidos por infraestructura (el gateway escucha en el puerto `8080`) |

---

## 2. Formato de errores (RFC 9457 *Problem Details*)

Toda respuesta con código `4xx` o `5xx` tiene `Content-Type: application/problem+json` y este
cuerpo:

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
| `detail` | string | Descripción legible para mostrar al usuario. Genérica a propósito (ver §4). |
| `instance` | string | Path de la petición que falló. |
| `timestamp` | string (ISO-8601) | Momento en que se generó el error. |
| `errors` | array | **Solo en `validation-error`.** Lista de errores por campo (ver §4). |

### Catálogo de `type`

| `type` | HTTP | Cuándo |
|---|---|---|
| `urn:problem-type:validation-error` | 400 | El cuerpo no cumple las reglas de formato. Incluye `errors[]`. |
| `urn:problem-type:duplicate-resource` | 409 | Los datos entran en conflicto con un usuario existente (local o ya registrado en el proveedor de identidad). |
| `urn:problem-type:resource-not-found` | 404 | El recurso solicitado no existe. (Sin uso en los endpoints actuales.) |
| `urn:problem-type:service-unavailable` | 503 | El microservicio destino (p. ej. `chat-registro`) no está disponible. **Propio del gateway**: `chat-registro` en solitario nunca lo devuelve. |
| `urn:problem-type:internal-error` | 500 | Error inesperado, incluido un fallo del microservicio destino que no sea "ya existe" ni "no disponible". `detail` siempre genérico; el detalle real queda en logs del servidor. |

---

## 3. Endpoints

### 3.1 `POST /api/v1/registro` — Registrar un usuario

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

`Content-Type: application/json`

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

#### Respuesta `503 Service Unavailable` — chat-registro no disponible

`type` = `urn:problem-type:service-unavailable`. El gateway no pudo alcanzar `chat-registro`
por gRPC (servicio caído, red interna). Reintentable con backoff.

```json
{
  "type": "urn:problem-type:service-unavailable",
  "title": "Servicio no disponible",
  "status": 503,
  "detail": "El servicio no esta disponible en este momento. Intentelo mas tarde.",
  "instance": "/api/v1/registro",
  "timestamp": "2026-09-09T03:10:00.123456Z"
}
```

#### Respuesta `500 Internal Server Error`

`type` = `urn:problem-type:internal-error`, `detail` genérico. Cualquier otro fallo no
cubierto arriba. Reintentable con backoff.

#### Ejemplo `curl`

```bash
curl -i -X POST http://localhost:8080/api/v1/registro \
  -H 'Content-Type: application/json' \
  -d '{"username":"mateo","email":"mateo@example.com","password":"Passw0rd!23"}'
```

---

## 4. Notas de integración para el frontend

1. **Ramifica por `type`, no por `status` ni por textos.** `title`/`detail` pueden cambiar de
   redacción sin previo aviso; `type` y `status` son estables.
2. **El `409` no dice qué campo colisiona.** Es intencional. Mensaje genérico en la UI.
3. **La contraseña nunca vuelve en ninguna respuesta.** No la guardes ni la muestres tras el
   alta.
4. **El `email` se normaliza a minúsculas** en el servidor; si tu UI lo muestra tras el alta,
   usa el valor devuelto en la respuesta, no el que tecleó el usuario.
5. **Valida la contraseña en el cliente con las mismas reglas de §3.1** para dar feedback
   inmediato; la validación del servidor es la autoritativa y devuelve `400` con `errors[]`.
6. **Unicidad de `username`/`email` no se puede pre-comprobar.** Se descubre al recibir el
   `409` del `POST`.
7. **Un `503` es distinto de un `500`.** El primero significa "el servicio destino está caído,
   probablemente reintentable pronto"; el segundo es un fallo inesperado. Trátalos distinto en
   la UI si tu app diferencia "servicio en mantenimiento" de "algo salió mal".

---

## 5. Modelos (TypeScript)

```ts
// Petición
export interface RegistroRequest {
  username: string; // 3–50, /^[A-Za-z0-9._-]+$/
  email: string;    // email válido, <= 255
  password: string; // 8–20; mayuscula + minuscula + numero + especial; sin 4+ repetidos
}

// Respuesta 201
export interface RegistroResponse {
  id: number;
  username: string;
  email: string;
  proveedor: string; // hoy siempre "password"
  activo: boolean;
  createdAt: string; // ISO-8601 UTC
}

// Error RFC 9457 (cualquier 4xx/5xx)
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

## 6. Cómo funciona por dentro

`RegistroController` valida el cuerpo (Bean Validation, mismas reglas que `chat-registro`) y
delega en `RegistroService`, que llama a `RegistroGrpcClient`. Ese cliente traduce el DTO a
`RegistrarUsuarioRequest` (proto), invoca `RegistroGrpcService/Registrar` en `chat-registro`
y traduce la respuesta o el error de vuelta al mismo formato REST. La orquestación real (alta
en el proveedor de identidad, reconciliación, compensación) vive en `chat-registro` — ver su
`docs/contratos-api.md` §6 — y es idéntica se llegue por REST directo o, como ahora, a través
de este gateway.

Ver también [`arquitectura-gateway.md`](arquitectura-gateway.md) para cómo se añade un
microservicio nuevo al gateway.

---

## 7. Otros recursos del servicio

| Recurso | Path | Uso |
|---|---|---|
| Swagger UI | `/swagger-ui.html` | Exploración interactiva |
| OpenAPI JSON | `/v3/api-docs` | Generación de clientes / tipos |
| Health check | `/actuator/health` | Monitorización / readiness |

---

## 8. Control de versiones de este documento

| Fecha | Cambio |
|---|---|
| 2026-09-13 | `chat-gateway` pasa a ser el punto de entrada REST; el contrato de `POST /api/v1/registro` no cambia para el cliente, pero ahora lo sirve el gateway (reenvío por gRPC a `chat-registro`). Se documenta el nuevo `503 service-unavailable`, propio del gateway. |
| (anterior) | Historial de cuando `chat-registro` se llamaba directo: ver `chat-registro/docs/contratos-api.md` §8. |
