package com.arquetipo.demo.conversacion.web;

import com.arquetipo.demo.conversacion.web.dto.MensajeResponse;
import com.arquetipo.demo.conversacion.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Conversaciones", description = "Historial de mensajes de una conversacion 1 a 1, enrutado a chat-conversacion")
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
}
