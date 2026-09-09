package com.arquetipo.demo.registro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * DTO de salida tras registrar un usuario. No expone la contrasena ni su hash.
 */
@Schema(name = "RegistroResponse", description = "Usuario registrado")
public record RegistroResponse(

		@Schema(description = "Identificador generado", example = "1")
		Long id,

		@Schema(description = "Nombre de usuario", example = "mateo")
		String username,

		@Schema(description = "Correo electronico (en minusculas)", example = "mateo@example.com")
		String email,

		@Schema(description = "Si la cuenta esta activa", example = "true")
		boolean activo,

		@Schema(description = "Instante de creacion (UTC)", example = "2026-09-08T20:53:47.441193Z")
		Instant createdAt
) {
}
