package com.arquetipo.demo.registro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos mínimos de un usuario ya registrado, devueltos por
 * {@code GET /api/v1/usuarios/{uid}}. Mismo contrato que {@code UsuarioBasico} en
 * {@code chat-registro}: no expone el uid (el cliente ya lo tiene, es el dato de entrada) ni
 * ningún otro campo del perfil más allá de username/email/avatar.
 */
@Schema(name = "UsuarioResponse", description = "Datos básicos de un usuario registrado")
public record UsuarioResponse(

		@Schema(description = "Nombre de usuario", example = "mateo")
		String username,

		@Schema(description = "Correo electrónico (en minúsculas)", example = "mateo@example.com")
		String email,

		@Schema(description = "Avatar del usuario. Null si no eligio ninguno.",
				example = "https://cdn.example.com/avatares/mateo.png", nullable = true)
		String avatar
) {
}
