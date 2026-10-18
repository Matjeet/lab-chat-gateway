package com.arquetipo.demo.common.auth;

import com.arquetipo.demo.registro.service.RegistroService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Validaciones de acceso que antes vivian dentro de cada controlador: autentica la peticion
 * (via {@link AutenticacionExtractor}) y comprueba que la identidad autenticada coincide con
 * el recurso pedido. El controlador solo llama al metodo que necesita y, si devuelve
 * {@code false}, responde 403 con su propio mensaje.
 *
 * <p>Un token ausente o invalido no devuelve {@code false}: {@link AutenticacionExtractor} lanza
 * {@code UnauthorizedException} (401) antes de llegar a comparar nada.
 *
 * <p>Depende de {@link RegistroService} solo para {@link #usernameCoincide}: el uid es un dato
 * de Firebase y el {@code username} lo conoce unicamente {@code chat-registro}, asi que hay que
 * resolver uno a partir del otro antes de comparar.
 */
@Slf4j
@Component
public class ValidadorAcceso {

	private final AutenticacionExtractor autenticacion;
	private final RegistroService registroService;

	public ValidadorAcceso(AutenticacionExtractor autenticacion, RegistroService registroService) {
		this.autenticacion = autenticacion;
		this.registroService = registroService;
	}

	/** Solo exige una sesion valida, sin comparar contra ningun recurso. */
	public void autenticar(String authorization) {
		log.debug(">> autenticar()");
		autenticacion.uidAutenticado(authorization);
		log.debug("<< autenticar() -> OK");
	}

	/** Si el uid del token es exactamente {@code uidEsperado}. */
	public boolean uidCoincide(String authorization, String uidEsperado) {
		log.debug(">> uidCoincide(uidEsperado='{}')", uidEsperado);
		boolean coincide = autenticacion.uidAutenticado(authorization).equals(uidEsperado);
		if (!coincide) {
			log.warn("Acceso denegado: el uid autenticado no coincide con el esperado. uid='{}'", uidEsperado);
		}
		log.debug("<< uidCoincide() -> {}", coincide);
		return coincide;
	}

	/** Si el {@code username} de la cuenta autenticada es exactamente {@code usernameEsperado}. */
	public boolean usernameCoincide(String authorization, String usernameEsperado) {
		log.debug(">> usernameCoincide(usernameEsperado='{}')", usernameEsperado);
		String uidAutenticado = autenticacion.uidAutenticado(authorization);
		boolean coincide = registroService.obtenerUsuario(uidAutenticado).username().equals(usernameEsperado);
		if (!coincide) {
			log.warn("Acceso denegado: el usuario autenticado no coincide con el esperado. username='{}'",
					usernameEsperado);
		}
		log.debug("<< usernameCoincide() -> {}", coincide);
		return coincide;
	}
}
