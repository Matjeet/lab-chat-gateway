package com.arquetipo.demo.registro.web.dto;

import java.time.Instant;

/**
 * DTO de salida tras registrar un usuario. No expone la contrasena ni su hash.
 */
public record RegistroResponse(
		Long id,
		String username,
		String email,
		boolean activo,
		Instant createdAt
) {
}
