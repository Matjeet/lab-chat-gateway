package com.arquetipo.demo.common.auth;

import com.arquetipo.demo.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Punto único que cualquier controlador del gateway usa para autenticar una petición:
 * extrae el token de la cabecera {@code Authorization: Bearer <idToken>} y lo verifica contra
 * {@link VerificadorTokenIdentidad}, devolviendo el {@code uid} ya autenticado.
 *
 * <p>Ni este componente ni el controlador que lo llama reenvían el token a ningún
 * microservicio interno — el {@code uid} que devuelve es lo único que viaja por gRPC. Un
 * microservicio nuevo que necesite autenticación solo tiene que inyectar esta clase, sin
 * integrarse con Firebase (ni con ningún otro proveedor de identidad) él mismo.
 */
@Slf4j
@Component
public class AutenticacionExtractor {

	private static final String PREFIJO_BEARER = "Bearer ";

	private final VerificadorTokenIdentidad verificador;

	public AutenticacionExtractor(VerificadorTokenIdentidad verificador) {
		this.verificador = verificador;
	}

	/**
	 * @param authorization valor tal cual de la cabecera {@code Authorization} (puede ser
	 *                       {@code null} si no vino)
	 * @return el uid verificado del token
	 * @throws UnauthorizedException si falta la cabecera, no tiene el prefijo {@code Bearer },
	 *                                o el token es inválido/expirado
	 */
	public String uidAutenticado(String authorization) {
		// Sin parametros en el log: authorization lleva el token, una credencial (ver "Reglas
		// de logging" en CLAUDE.md).
		log.debug(">> uidAutenticado()");
		String uid = verificador.verificar(extraerToken(authorization));
		log.debug("<< uidAutenticado() -> OK, uid='{}'", uid);
		return uid;
	}

	private String extraerToken(String authorization) {
		log.debug(">> extraerToken()");
		if (authorization == null || !authorization.startsWith(PREFIJO_BEARER)) {
			log.warn("Cabecera Authorization ausente o sin el prefijo Bearer");
			throw new UnauthorizedException("Falta la cabecera Authorization: Bearer <idToken>");
		}
		String idToken = authorization.substring(PREFIJO_BEARER.length()).trim();
		if (idToken.isBlank()) {
			log.warn("Cabecera Authorization con token vacio");
			throw new UnauthorizedException("Falta la cabecera Authorization: Bearer <idToken>");
		}
		log.debug("<< extraerToken() -> OK");
		return idToken;
	}
}
