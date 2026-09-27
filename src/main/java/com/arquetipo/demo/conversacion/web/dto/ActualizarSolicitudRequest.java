package com.arquetipo.demo.conversacion.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos para aceptar o rechazar una solicitud de chat, tal como los recibe el gateway del
 * cliente REST. Mismas reglas de formato que {@link SolicitudChatRequest}.
 *
 * <p>{@code solicitado} debe ser el {@code username} del usuario autenticado — lo compara
 * {@link com.arquetipo.demo.conversacion.web.ConversacionController#actualizarSolicitud}, no
 * esta anotacion: solo quien recibio la solicitud puede aceptarla o rechazarla, nunca quien la
 * envio.
 */
@Schema(name = "ActualizarSolicitudRequest", description = "Datos para aceptar o rechazar una solicitud de chat")
public record ActualizarSolicitudRequest(

		@Schema(
				description = "Username (chat-registro) de quien envio la solicitud originalmente.",
				example = "mateo", minLength = 3, maxLength = 50)
		@NotBlank
		@Size(min = 3, max = 50)
		@Pattern(regexp = "^[a-zA-Z0-9._-]+$",
				message = "solo admite letras, numeros y los signos . _ -")
		String solicitante,

		@Schema(
				description = "Username (chat-registro) de quien la recibio. Debe ser el usuario "
						+ "autenticado.",
				example = "ana", minLength = 3, maxLength = 50)
		@NotBlank
		@Size(min = 3, max = 50)
		@Pattern(regexp = "^[a-zA-Z0-9._-]+$",
				message = "solo admite letras, numeros y los signos . _ -")
		String solicitado,

		@Schema(description = "true = aceptar, false = rechazar", example = "true")
		boolean aceptada
) {
}
