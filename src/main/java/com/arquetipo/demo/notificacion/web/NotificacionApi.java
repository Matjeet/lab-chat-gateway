package com.arquetipo.demo.notificacion.web;

import com.arquetipo.demo.notificacion.web.dto.MarcarLeidaRequest;
import com.arquetipo.demo.notificacion.web.dto.NotificacionResponse;
import com.arquetipo.demo.notificacion.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Contrato OpenAPI de las notificaciones de un usuario, tal como el gateway las expone al
 * cliente. Ambos endpoints exigen autenticación, con distinto mecanismo de comparación — ver
 * {@link NotificacionController}.
 */
@Tag(name = "Notificaciones", description = "Notificaciones de un usuario, enrutadas a chat-notificaciones")
public interface NotificacionApi {

	@Operation(
			summary = "Listar las notificaciones de un usuario",
			description = """
					Requiere `Authorization: Bearer <idToken>` — mismo mecanismo que
					`GET /api/v1/conversaciones/{usuario}/chats`: el gateway verifica el token él
					mismo, resuelve el `username` del uid autenticado y comprueba que coincide con
					`receptor`; un token válido de otro usuario no autoriza a leer esta bandeja.

					No valida que `receptor` exista en `chat-registro`: un valor que no exista, o
					que no tenga notificaciones, responde `200` con `content: []`, nunca `404`.
					Paginada por página/offset (no por cursor, a diferencia de la lista de chats),
					más reciente primero por defecto.
					""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Página de notificaciones (puede estar vacía)",
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = PageResponse.class),
							examples = @ExampleObject(value = """
									{
									  "content": [
									    {
									      "id": 1,
									      "remitente": "mateo",
									      "tipo": "solicitud",
									      "leida": false,
									      "createdAt": "2026-09-25T20:53:47.441193Z",
									      "meta": "{\\"aceptada\\":false,\\"pendiente\\":true}",
									      "avatarRemitente": "https://cdn.example.com/avatares/mateo.png"
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
									"""))),
			@ApiResponse(
					responseCode = "401",
					description = "Falta la cabecera Authorization, o el idToken es inválido/expirado",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "403",
					description = "El idToken es válido pero pertenece a un usuario distinto de receptor",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "503",
					description = "chat-notificaciones o chat-registro no estan disponibles en este momento.",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class)))
	})
	PageResponse<NotificacionResponse> listaNotificaciones(
			@Parameter(description = "Usuario cuyas notificaciones se piden", example = "mateo") String receptor,
			String authorization,
			@Parameter(description = "Página, 0-indexada") int page,
			@Parameter(description = "Tamaño de página (máximo 100, aplicado por chat-notificaciones)") int size,
			@Parameter(description = "Formato 'campo,direccion', ej. 'createdAt,asc'") String sort);

	@Operation(
			summary = "Marcar una notificación como leída o no leída",
			description = """
					Requiere `Authorization: Bearer <idToken>` — a diferencia de los demás
					endpoints autenticados, aquí el gateway compara el uid autenticado
					**directamente** contra el `uid` del cuerpo (mismo mecanismo que
					`GET /api/v1/usuarios/{uid}`), sin resolver nada contra `chat-registro`. Esto
					prueba que quien llama es quien dice ser, pero **no** que sea el receptor real
					de esa notificación — `chat-notificaciones` todavía no vincula una
					notificación a un uid ni a un username (ver `docs/contratos-api.md`).
					""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Notificación actualizada",
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = NotificacionResponse.class),
							examples = @ExampleObject(value = """
									{
									  "id": 1,
									  "remitente": "mateo",
									  "tipo": "solicitud",
									  "leida": true,
									  "createdAt": "2026-09-25T20:53:47.441193Z",
									  "meta": "{\\"aceptada\\":false,\\"pendiente\\":true}",
									  "avatarRemitente": "https://cdn.example.com/avatares/mateo.png"
									}
									"""))),
			@ApiResponse(
					responseCode = "400",
					description = "El cuerpo no cumple el formato (falta uid)",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "401",
					description = "Falta la cabecera Authorization, o el idToken es inválido/expirado",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "403",
					description = "El idToken es válido pero de un uid distinto al del cuerpo",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "404",
					description = "Ninguna notificación con ese id",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "503",
					description = "chat-notificaciones no esta disponible en este momento.",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class)))
	})
	NotificacionResponse actualizarLeida(
			@Parameter(description = "Identificador de la notificación", example = "1") long id,
			MarcarLeidaRequest request,
			String authorization);
}
