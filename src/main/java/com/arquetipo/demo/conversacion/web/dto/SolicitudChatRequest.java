package com.arquetipo.demo.conversacion.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear una solicitud de chat, tal como los recibe el gateway del cliente REST.
 * Mismas reglas de formato que exige {@code chat-conversacion} (ver su
 * {@code docs/contrato-grpc-conversacion.md} §6): el gateway las aplica primero para devolver
 * un 400 inmediato sin siquiera llamar por gRPC.
 *
 * <p>{@code solicitante} debe ser el {@code username} del usuario autenticado — lo compara
 * {@link com.arquetipo.demo.conversacion.web.ConversacionController#crearSolicitud}, no esta
 * anotacion: nadie puede crear una solicitud en nombre de otro usuario.
 */
@Schema(name = "SolicitudChatRequest", description = "Datos para crear una solicitud de chat")
public record SolicitudChatRequest(

		@Schema(
				description = "Username (chat-registro) de quien inicia la solicitud. Debe ser "
						+ "el usuario autenticado.",
				example = "mateo", minLength = 3, maxLength = 50)
		@NotBlank
		@Size(min = 3, max = 50)
		@Pattern(regexp = "^[a-zA-Z0-9._-]+$",
				message = "solo admite letras, numeros y los signos . _ -")
		String solicitante,

		@Schema(
				description = "Username (chat-registro) de quien recibe la solicitud. Distinto de solicitante.",
				example = "ana", minLength = 3, maxLength = 50)
		@NotBlank
		@Size(min = 3, max = 50)
		@Pattern(regexp = "^[a-zA-Z0-9._-]+$",
				message = "solo admite letras, numeros y los signos . _ -")
		String solicitado
) {
}
