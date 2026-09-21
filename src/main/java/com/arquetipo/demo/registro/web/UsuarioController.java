package com.arquetipo.demo.registro.web;

import com.arquetipo.demo.common.auth.AutenticacionExtractor;
import com.arquetipo.demo.common.exception.ForbiddenException;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.UsuarioResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint público de consulta de datos básicos de un usuario. El contrato OpenAPI vive en
 * {@link UsuarioApi}; aquí solo queda el enrutado y la autorización.
 *
 * <p>Es el único endpoint del gateway que exige autenticación, y la resuelve él mismo con
 * {@link AutenticacionExtractor} (Firebase Admin SDK por debajo, ver
 * {@code common.auth.firebase}) — <b>{@code chat-registro} nunca ve el token</b>, solo recibe
 * el {@code uid} una vez que el gateway ya comprobó que es válido y que coincide con el
 * recurso pedido. Sin una cabecera {@code Authorization: Bearer <idToken>} bien formada, o con
 * un token inválido/expirado, se rechaza con 401 sin llamar por gRPC; con un token válido pero
 * de otro uid, con 403.
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
}
