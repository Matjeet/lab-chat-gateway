package com.arquetipo.demo.registro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada del alta de un usuario.
 */
@Schema(name = "RegistroRequest", description = "Datos para dar de alta un usuario")
public record RegistroRequest(

		@Schema(
				description = "Nombre de usuario unico. Solo letras, numeros y los signos . _ -",
				example = "mateo",
				minLength = 3, maxLength = 50)
		@NotBlank
		@Size(min = 3, max = 50)
		@Pattern(regexp = "^[a-zA-Z0-9._-]+$",
				message = "solo admite letras, numeros y los signos . _ -")
		String username,

		@Schema(
				description = "Correo electronico unico. Se normaliza a minusculas.",
				example = "mateo@example.com",
				maxLength = 255)
		@NotBlank
		@Email
		@Size(max = 255)
		String email,

		@Schema(
				description = "Contrasena en claro. Se almacena solo como hash BCrypt.",
				example = "secretpass",
				minLength = 8, maxLength = 100,
				format = "password")
		@NotBlank
		@Size(min = 8, max = 100)
		String password
) {
}
