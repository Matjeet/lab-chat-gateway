# Arquitectura de chat-gateway

`chat-gateway` es el único punto de entrada del sistema: el cliente (frontend, app móvil)
solo le habla a él, por **REST**. El gateway no implementa reglas de negocio propias — valida
el formato del cuerpo y reenvía cada petición al microservicio correspondiente por **gRPC**
(protocolo interno, no expuesto al cliente).

```
Cliente ──REST──▶ chat-gateway ──gRPC──▶ chat-registro
                              ──gRPC──▶ (futuro microservicio 2)
                              ──gRPC──▶ (futuro microservicio N)
```

## Por qué REST fuera, gRPC dentro

- **Cliente ↔ gateway (REST/JSON):** es el protocolo que ya conocen los clientes web/móvil,
  cacheable por proxies e inspeccionable con curl/Postman sin tooling adicional.
- **Gateway ↔ microservicios (gRPC):** contratos tipados por `.proto`, generación de stubs,
  HTTP/2 multiplexado — apropiado para tráfico interno de red controlada donde ambos lados son
  código propio.

El cliente REST **no sabe** que por debajo hay gRPC: el contrato JSON documentado en
`docs/contratos-api.md` no cambia si un día se sustituye el transporte interno por otra cosa.

## Paquete por microservicio destino

Cada microservicio al que el gateway enruta tiene su propia carpeta bajo
`com.arquetipo.demo`, con la misma forma que `registro/`:

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

`src/main/proto/<servicio>.proto` es una **copia exacta** del `.proto` que documenta el
microservicio destino (ver `contrato-grpc-<servicio>.md` en su propio repo) — no se reescribe,
solo se copia tal cual para que el gradle plugin `com.google.protobuf` genere el stub.

## Cómo añadir un microservicio nuevo

1. Pide al equipo dueño del microservicio su `contrato-grpc-<servicio>.md` (o el `.proto`
   directamente) y su `contratos-api.md` si además define cómo debe verse expuesto al cliente.
2. Copia el `.proto` a `src/main/proto/<servicio>.proto`.
3. Crea el paquete `<servicio>/` con la misma forma que `registro/` de este documento.
4. Añade la dirección del microservicio a `application.yml` bajo `servicios.<servicio>` (host
   + puerto, ambos con variable de entorno) y su `Properties`/`ClientConfig` en `<servicio>/grpc/`.
5. Traduce en `<servicio>Api`/`<servicio>Request`/`<servicio>Response` el mismo contrato JSON
   que el cliente ya espera (o el que se defina para el endpoint nuevo).
6. Mapea los códigos gRPC (`ALREADY_EXISTS`, `INVALID_ARGUMENT`, `UNAVAILABLE`, ...) a las
   excepciones de `com.arquetipo.demo.common.exception` que ya traduce `GlobalExceptionHandler`
   — reutilízalas en vez de crear un manejador nuevo por servicio, salvo que el microservicio
   introduzca un tipo de error genuinamente nuevo.
7. Documenta el endpoint nuevo en `docs/contratos-api.md` (o en un archivo aparte si la lista
   crece mucho, p. ej. `docs/contratos-api-<servicio>.md`).

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
