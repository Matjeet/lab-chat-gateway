package com.arquetipo.demo.conversacion.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Mensaje ya persistido por {@code chat-conversacion}: es lo que se reenvia por el WebSocket
 * a ambos extremos de la conversacion y lo que devuelve el historial. Mismo contrato que
 * {@code MensajeResponse} en {@code chat-conversacion}.
 */
@Schema(name = "MensajeResponse", description = "Mensaje de texto de una conversacion")
public record MensajeResponse(

		@Schema(description = "Identificador generado", example = "66f1c2a8b4c9a12345678901")
		String id,

		@Schema(description = "Usuario que envia el mensaje", example = "mateo")
		String remitente,

		@Schema(description = "Usuario que lo recibe", example = "ana")
		String destinatario,

		@Schema(description = "Contenido de texto del mensaje", example = "Hola!")
		String contenido,

		@Schema(description = "Instante de envio (UTC)", example = "2026-09-15T20:53:47.441193Z")
		Instant enviadoEn
) {
}
