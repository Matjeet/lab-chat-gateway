package com.arquetipo.demo.registro.web;

import com.arquetipo.demo.common.auth.AutenticacionExtractor;
import com.arquetipo.demo.common.exception.ForbiddenException;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.ExisteUsernameResponse;
import com.arquetipo.demo.registro.web.dto.UsuarioResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos de consulta sobre usuarios ya registrados. El contrato OpenAPI vive en
 * {@link UsuarioApi}; aquí solo queda el enrutado y la autorización.
 *
 * <p>Los dos exigen autenticación (resuelta con {@link AutenticacionExtractor}, Firebase
 * Admin SDK por debajo, ver {@code common.auth.firebase}) — <b>{@code chat-registro} nunca ve
 * el token</b>, solo recibe el dato ya autenticado que necesita — pero con alcances distintos:
 * {@link #obtenerUsuario} exige además que el uid autenticado **coincida** con el recurso
 * pedido (403 si no); {@link #existeUsername} solo exige estar autenticado, sin comparar
 * contra nada — cualquier usuario puede preguntar por la disponibilidad de cualquier
 * {@code username} (p. ej. para saber si puede iniciar un chat con él), igual que permite
 * {@code chat-registro} sin token alguno (ver {@code contrato-grpc-registro.md} §1 de
 * chat-registro).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController implements UsuarioApi {

	private final RegistroService service;
	private final AutenticacionExtractor autenticacion;

	public UsuarioController(RegistroService service, AutenticacionExtractor autenticacion) {
		this.service = service;
		this.autenticacion = autenticacion;
	}

	@Override
	@GetMapping("/{uid}")
	public UsuarioResponse obtenerUsuario(
			@PathVariable String uid,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		log.debug(">> obtenerUsuario(uid='{}')", uid);
		String uidAutenticado = autenticacion.uidAutenticado(authorization);
		if (!uidAutenticado.equals(uid)) {
			log.warn("Acceso denegado: el uid autenticado no coincide con el solicitado. uid='{}'", uid);
			throw new ForbiddenException("El token no autoriza a consultar este usuario");
		}
		UsuarioResponse respuesta = service.obtenerUsuario(uid);
		log.debug("<< obtenerUsuario() -> OK, username='{}'", respuesta.username());
		return respuesta;
	}

	@Override
	@GetMapping("/existe")
	public ExisteUsernameResponse existeUsername(
			@RequestParam String username,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		log.debug(">> existeUsername(username='{}')", username);
		autenticacion.uidAutenticado(authorization);
		boolean existe = service.existeUsername(username);
		log.debug("<< existeUsername() -> OK, existe={}", existe);
		return new ExisteUsernameResponse(existe);
	}
}
