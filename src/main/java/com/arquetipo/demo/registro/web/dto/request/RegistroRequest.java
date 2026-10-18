package com.arquetipo.demo.registro.web.dto.request;

import com.arquetipo.demo.common.Constantes.Texto;
import com.arquetipo.demo.common.Constantes.Validacion;
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
				minLength = Validacion.USERNAME_LONGITUD_MINIMA, maxLength = Validacion.USERNAME_LONGITUD_MAXIMA)
		@NotBlank
		@Size(min = Validacion.USERNAME_LONGITUD_MINIMA, max = Validacion.USERNAME_LONGITUD_MAXIMA)
		@Pattern(regexp = Validacion.USERNAME_PATRON,
				message = Validacion.USERNAME_PATRON_MENSAJE)
		String username,

		@Schema(
				description = "Correo electronico unico. Se normaliza a minusculas.",
				example = "mateo@example.com",
				maxLength = Validacion.EMAIL_LONGITUD_MAXIMA)
		@NotBlank
		@Email
		@Size(max = Validacion.EMAIL_LONGITUD_MAXIMA)
		String email,

		@Schema(
				description = "Contrasena en claro: el gateway solo la transporta hasta "
						+ "chat-registro, que la reenvia al proveedor de identidad. No se "
						+ "persiste ni se loguea en ningun punto del gateway.",
				example = "Passw0rd!23",
				minLength = Validacion.PASSWORD_LONGITUD_MINIMA, maxLength = Validacion.PASSWORD_LONGITUD_MAXIMA,
				format = "password")
		@NotBlank
		@Size(min = Validacion.PASSWORD_LONGITUD_MINIMA, max = Validacion.PASSWORD_LONGITUD_MAXIMA)
		@Pattern(regexp = Validacion.PASSWORD_PATRON, message = Validacion.PASSWORD_PATRON_MENSAJE)
		String password,

		@Schema(
				description = "Avatar del usuario: un enlace http(s), o una etiqueta "
						+ "<Blobatar .../> (avatar animado) que el frontend renderiza tal cual. "
						+ "Opcional -- se puede omitir o mandar cadena vacia.",
				example = "https://cdn.example.com/avatares/mateo.png",
				maxLength = Validacion.AVATAR_LONGITUD_MAXIMA, nullable = true)
		@Size(max = Validacion.AVATAR_LONGITUD_MAXIMA)
		@Pattern(regexp = Validacion.AVATAR_PATRON, message = Validacion.AVATAR_PATRON_MENSAJE)
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
		if (recortado.length() >= Texto.LONGITUD_MINIMA_ENVUELTO_EN_COMILLAS) {
			char primero = recortado.charAt(Texto.INDICE_PRIMER_CARACTER);
			char ultimo = recortado.charAt(recortado.length() - Texto.LONGITUD_UN_CARACTER);
			boolean envueltoEnComillas = (primero == Texto.COMILLA_DOBLE && ultimo == Texto.COMILLA_DOBLE)
					|| (primero == Texto.COMILLA_SIMPLE && ultimo == Texto.COMILLA_SIMPLE);
			if (envueltoEnComillas) {
				recortado = recortado
						.substring(Texto.LONGITUD_UN_CARACTER, recortado.length() - Texto.LONGITUD_UN_CARACTER)
						.trim();
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
