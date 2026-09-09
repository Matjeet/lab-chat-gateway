package com.arquetipo.demo.registro.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada del alta de un usuario.
 */
public record RegistroRequest(

		@NotBlank
		@Size(min = 3, max = 50)
		@Pattern(regexp = "^[a-zA-Z0-9._-]+$",
				message = "solo admite letras, numeros y los signos . _ -")
		String username,

		@NotBlank
		@Email
		@Size(max = 255)
		String email,

		@NotBlank
		@Size(min = 8, max = 100)
		String password
) {
}
