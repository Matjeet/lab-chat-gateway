package com.arquetipo.demo.conversacion.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resumen de una conversacion 1 a 1 desde el punto de vista del usuario que pidio la lista:
 * quien es la otra persona (con su avatar, si lo tiene) y cual fue el ultimo mensaje entre
 * ambos (en cualquiera de los dos sentidos). Mismo contrato que {@code ChatResumen} en
 * {@code chat-conversacion}.
 */
@Schema(name = "ChatResumen",
		description = "Resumen de un chat: la otra persona, su avatar y el ultimo mensaje entre ambos")
public record ChatResumen(

		@Schema(description = "La otra persona de la conversacion", example = "ana")
		String otroUsuario,

		@Schema(description = "Avatar de la otra persona (enlace http(s) o etiqueta <Blobatar .../>, tal cual "
				+ "lo guardo el registro). Null si no eligio ninguno o si todavia no hay perfil guardado para ella.",
				example = "https://cdn.example.com/avatares/ana.png", nullable = true)
		String avatar,

		@Schema(description = "El mensaje mas reciente entre ambos, en cualquiera de los dos sentidos")
		MensajeResponse ultimoMensaje
) {
}
