package com.arquetipo.demo.conversacion.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resumen de una conversacion 1 a 1 desde el punto de vista del usuario que pidio la lista:
 * quien es la otra persona y cual fue el ultimo mensaje entre ambos (en cualquiera de los dos
 * sentidos). Mismo contrato que {@code ChatResumen} en {@code chat-conversacion}.
 */
@Schema(name = "ChatResumen", description = "Resumen de un chat: la otra persona y el ultimo mensaje entre ambos")
public record ChatResumen(

		@Schema(description = "La otra persona de la conversacion", example = "ana")
		String otroUsuario,

		@Schema(description = "El mensaje mas reciente entre ambos, en cualquiera de los dos sentidos")
		MensajeResponse ultimoMensaje
) {
}
