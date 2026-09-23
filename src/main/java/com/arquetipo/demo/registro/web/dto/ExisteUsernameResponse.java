package com.arquetipo.demo.registro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Disponibilidad de un {@code username}, devuelta por {@code GET /api/v1/usuarios/existe}.
 * Mismo contrato que {@code ExisteUsernameResponse} en {@code chat-registro}: un único booleano,
 * sin distinguir mayúsculas ni exponer nada más del usuario si ya existe.
 */
@Schema(name = "ExisteUsernameResponse", description = "Si un username ya esta en uso")
public record ExisteUsernameResponse(

		@Schema(description = "true si ya hay un usuario con ese username (sin distinguir mayúsculas)", example = "true")
		boolean existe
) {
}
