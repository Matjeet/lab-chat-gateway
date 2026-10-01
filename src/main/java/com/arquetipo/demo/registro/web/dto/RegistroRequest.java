package com.arquetipo.demo.registro.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada del alta de un usuario, tal como lo recibe el gateway del cliente REST.
 *
 * <p>Mismas reglas que valida {@code chat-registro} (ver su
 * {@code docs/contratos-api.md} §3.1): el gateway las aplica primero para devolver un 400
 * inmediato sin ni siquiera llamar por gRPC, y {@code chat-registro} las vuelve a aplicar
 * como autoridad final. El gateway no persiste ni reenvia la contrasena a ningun sitio
 * propio: solo la transporta hasta el microservicio.
 *
 * <p>{@code avatar} se normaliza en el constructor compacto (ver {@link #normalizarAvatar}),
 * antes de que Bean Validation evalue el {@code @Pattern} — mismo punto donde lo hace
 * {@code chat-registro} (en su {@code RegistroGrpcMapper}, antes de construir su propio
 * {@code RegistroRequest}). Sin esto, un cliente que manda {@code ""} (el valor natural de un
 * formulario sin avatar elegido) recibiria aqui un 400 que {@code chat-registro} nunca
 * devolveria para el mismo valor — el contrato documenta {@code ""} como equivalente a "sin
 * avatar", no como un formato invalido.
 */
@Schema(name = "RegistroRequest", description = "Datos para registrar un usuario nuevo")
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
				description = "Contrasena en claro: el gateway solo la transporta hasta "
						+ "chat-registro, que la reenvia al proveedor de identidad. No se "
						+ "persiste ni se loguea en ningun punto del gateway.",
				example = "Passw0rd!23",
				minLength = 8, maxLength = 20,
				format = "password")
		@NotBlank
		@Size(min = 8, max = 20)
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])(?!.*(.)\\1{3,}).+$",
				message = "debe tener mayuscula, minuscula, numero y caracter especial, "
						+ "y ningun caracter repetido 4 o mas veces seguidas")
		String password,

		@Schema(
				description = "Avatar del usuario: un enlace http(s), o una etiqueta "
						+ "<Blobatar .../> (avatar animado) que el frontend renderiza tal cual. "
						+ "Opcional -- se puede omitir o mandar cadena vacia.",
				example = "https://cdn.example.com/avatares/mateo.png",
				maxLength = 500, nullable = true)
		@Size(max = 500)
		@Pattern(
				regexp = "^(https?://[^\\s\"'<>]+|<Blobatar(\\s[^<>\\r\\n]*)?/>)$",
				message = "debe ser un enlace http(s) o una etiqueta <Blobatar ... /> en una sola linea, "
						+ "sin saltos de linea")
		String avatar
) {

	public RegistroRequest {
		avatar = normalizarAvatar(avatar);
	}

	/**
	 * proto3 no distingue "avatar ausente" de cadena vacia, y un formulario sin avatar elegido
	 * manda igual de forma natural {@code ""}: se trata como "sin avatar" (null), para que el
	 * {@code @Pattern} de arriba no rechace la ausencia del campo como si fuera un formato
	 * invalido — mismo criterio que {@code chat-registro} (ver su {@code RegistroGrpcMapper}).
	 *
	 * <p>Si el valor no esta vacio pero llega envuelto en un unico par de comillas rectas
	 * (simples o dobles) que encierran toda la cadena, se retira ese par exterior, sin alterar
	 * nada mas del contenido.
	 */
	private static String normalizarAvatar(String avatarCrudo) {
		if (avatarCrudo == null) {
			return null;
		}
		String recortado = avatarCrudo.trim();
		if (recortado.isEmpty()) {
			return null;
		}
		if (recortado.length() >= 2) {
			char primero = recortado.charAt(0);
			char ultimo = recortado.charAt(recortado.length() - 1);
			boolean envueltoEnComillas = (primero == '"' && ultimo == '"') || (primero == '\'' && ultimo == '\'');
			if (envueltoEnComillas) {
				recortado = recortado.substring(1, recortado.length() - 1).trim();
			}
		}
		return recortado.isEmpty() ? null : recortado;
	}

	/** Nunca incluir la contrasena en logs, ni siquiera por accidente via un log de este record. */
	@Override
	public String toString() {
		return "RegistroRequest[username=%s, email=%s, avatar=%s, password=***]"
				.formatted(username, email, avatar);
	}
}
