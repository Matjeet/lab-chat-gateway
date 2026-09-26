package com.arquetipo.demo.conversacion.web;

import com.arquetipo.demo.conversacion.web.dto.ChatResumen;
import com.arquetipo.demo.conversacion.web.dto.CursorPage;
import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import com.arquetipo.demo.conversacion.web.dto.SolicitudChatRequest;
import com.arquetipo.demo.conversacion.web.dto.SolicitudChatResponse;
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
 * Contrato OpenAPI del historial de una conversacion, tal como lo expone el gateway al
 * cliente. Mismo contrato que documenta {@code chat-conversacion} en su
 * {@code docs/contratos-api.md} §3 — el envio de mensajes nuevos va por el WebSocket
 * ({@code /ws/chat/{usuario}}, ver {@link ChatWebSocketConfig}), que no forma parte de
 * OpenAPI/Swagger.
 */
@Tag(name = "Conversaciones", description = "Historial, lista de chats y solicitudes de chat, enrutados a chat-conversacion")
public interface ConversacionApi {

	@Operation(summary = "Historial paginado de una conversacion entre dos usuarios",
			description = """
					Devuelve los mensajes entre `usuarioA` y `usuarioB` (el orden no importa,
					se buscan en ambos sentidos), ordenados por fecha de envio ascendente por
					defecto. Si no hay mensajes, responde `200` con `content: []`, nunca `404`.
					""")
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Pagina de mensajes (puede estar vacia)",
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = PageResponse.class),
							examples = @ExampleObject(value = """
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
									"""))),
			@ApiResponse(
					responseCode = "503",
					description = "chat-conversacion no esta disponible en este momento.",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class)))
	})
	PageResponse<MensajeResponse> historial(
			@Parameter(description = "Un participante de la conversacion", example = "mateo") String usuarioA,
			@Parameter(description = "El otro participante", example = "ana") String usuarioB,
			@Parameter(description = "Pagina, 0-indexada") int page,
			@Parameter(description = "Tamaño de pagina (maximo 100, aplicado por chat-conversacion)") int size,
			@Parameter(description = "Formato 'campo,direccion', ej. 'enviadoEn,desc'") String sort);

	@Operation(summary = "Lista de chats de un usuario, con el ultimo mensaje de cada uno",
			description = """
					Requiere `Authorization: Bearer <idToken>` con el token de ID de Firebase de
					quien pregunta — mismo mecanismo que `GET /api/v1/usuarios/{uid}`: el gateway
					verifica el token él mismo, resuelve el `username` del uid autenticado (contra
					`chat-registro`) y comprueba que coincide con `usuario`; un token válido de otro
					usuario no autoriza a leer esta lista.

					Un resumen por cada persona con la que `usuario` tiene al menos un mensaje (en
					cualquiera de los dos sentidos), con el ultimo mensaje de esa conversacion,
					ordenados por fecha de ese ultimo mensaje (mas reciente primero). Paginado por
					**cursor**, no por pagina/offset (pensado para scroll infinito): manda el
					`nextCursor` de la respuesta anterior tal cual, sin modificarlo, para pedir la
					siguiente pagina. Si `usuario` no tiene ningun mensaje con nadie, responde `200`
					con `content: []`, nunca `404`.
					""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Pagina de chats (puede estar vacia)",
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = CursorPage.class),
							examples = @ExampleObject(value = """
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
									"""))),
			@ApiResponse(
					responseCode = "400",
					description = "El `cursor` no viene de un `nextCursor` real de chat-conversacion.",
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
					description = "El idToken es válido pero pertenece a un usuario distinto al pedido",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "503",
					description = "chat-conversacion o chat-registro no estan disponibles en este momento.",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class)))
	})
	CursorPage<ChatResumen> listaChats(
			@Parameter(description = "Usuario cuya lista de chats se pide", example = "mateo") String usuario,
			String authorization,
			@Parameter(description = "El nextCursor de una pagina anterior. Vacio = primera pagina.") String cursor,
			@Parameter(description = "Tamaño de pagina (maximo 100, aplicado por chat-conversacion)") int size);

	@Operation(summary = "Crear una solicitud de chat hacia otro usuario",
			description = """
					Requiere `Authorization: Bearer <idToken>` — mismo mecanismo que
					`GET /api/v1/conversaciones/{usuario}/chats`: el gateway verifica el token él
					mismo, resuelve el `username` del uid autenticado y comprueba que coincide con
					`solicitante`; un token válido de otro usuario no autoriza a crear la
					solicitud (no se puede solicitar chatear en nombre de alguien más).

					Paso previo obligatorio para poder chatear con alguien. `solicitante` y
					`solicitado` deben existir en `chat-registro` y ser distintos entre sí; no
					puede existir ya una solicitud **pendiente** entre ambos, en cualquier
					sentido (una solicitud ya resuelta no bloquea una nueva). La solicitud nace
					siempre con `aceptada: false` y `pendiente: true` — aceptarla o rechazarla no
					está implementado todavía, así que por ahora se queda pendiente para siempre.
					""",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(
					responseCode = "201",
					description = "Solicitud creada",
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(implementation = SolicitudChatResponse.class),
							examples = @ExampleObject(value = """
									{
									  "id": "66f1c2a8b4c9a12345678901",
									  "solicitante": "mateo",
									  "solicitado": "ana",
									  "aceptada": false,
									  "creadaEn": "2026-09-23T20:53:47.441193Z",
									  "pendiente": true
									}
									"""))),
			@ApiResponse(
					responseCode = "400",
					description = "El cuerpo no cumple el formato, o solicitante y solicitado son el mismo usuario.",
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
					description = "El idToken es válido pero pertenece a un usuario distinto de solicitante",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "404",
					description = "solicitante o solicitado no existen en chat-registro",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "409",
					description = "Ya existe una solicitud pendiente entre solicitante y solicitado, en cualquier sentido",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "503",
					description = "chat-conversacion o chat-registro no estan disponibles en este momento.",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class)))
	})
	SolicitudChatResponse crearSolicitud(SolicitudChatRequest request, String authorization);
}
