package com.arquetipo.demo.notificacion.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Datos para marcar una notificacion como leida o no leida, tal como los recibe el gateway del
 * cliente REST. {@code uid} no viaja a {@code chat-notificaciones} (ese servicio no valida
 * identidad todavia, ver su README): existe solo para que
 * {@link com.arquetipo.demo.notificacion.web.NotificacionController#actualizarLeida} compare
 * contra el uid autenticado — debe ser el mismo, o se rechaza con 403 sin llamar por gRPC.
 */
@Schema(name = "MarcarLeidaRequest", description = "Datos para marcar una notificacion como leida o no leida")
public record MarcarLeidaRequest(

		@Schema(
				description = "UID de Firebase de quien hace la peticion. Debe ser el uid autenticado.",
				example = "0lSUQS1RdYauzu3ifx6izoyzkvt2")
		@NotBlank
		String uid,

		@Schema(description = "Nuevo valor de leida", example = "true")
		boolean leida
) {
}
