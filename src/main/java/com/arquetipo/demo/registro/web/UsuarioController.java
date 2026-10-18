package com.arquetipo.demo.registro.web;

import com.arquetipo.demo.common.Constantes.Mensajes;
import com.arquetipo.demo.common.Constantes.Rutas;
import com.arquetipo.demo.common.auth.ValidadorAcceso;
import com.arquetipo.demo.common.exception.ForbiddenException;
import com.arquetipo.demo.registro.service.RegistroService;
import com.arquetipo.demo.registro.web.dto.response.ExisteUsernameResponse;
import com.arquetipo.demo.registro.web.dto.response.UsuarioResponse;
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
 * <p>Los dos exigen autenticación (resuelta con {@link ValidadorAcceso}, Firebase
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
@RequestMapping(Rutas.USUARIOS)
public class UsuarioController implements UsuarioApi {

	private final RegistroService service;
	private final ValidadorAcceso validador;

	public UsuarioController(RegistroService service, ValidadorAcceso validador) {
		this.service = service;
		this.validador = validador;
	}

	@Override
	@GetMapping(Rutas.USUARIO_POR_UID)
	public UsuarioResponse obtenerUsuario(
			@PathVariable String uid,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		log.debug(">> obtenerUsuario(uid='{}')", uid);
		if (!validador.uidCoincide(authorization, uid)) {
			throw new ForbiddenException(Mensajes.SIN_PERMISO_CONSULTAR_USUARIO);
		}
		UsuarioResponse respuesta = service.obtenerUsuario(uid);
		log.debug("<< obtenerUsuario() -> OK, username='{}'", respuesta.username());
		return respuesta;
	}

	@Override
	@GetMapping(Rutas.USUARIO_EXISTE)
	public ExisteUsernameResponse existeUsername(
			@RequestParam String username,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		log.debug(">> existeUsername(username='{}')", username);
		validador.autenticar(authorization);
		boolean existe = service.existeUsername(username);
		log.debug("<< existeUsername() -> OK, existe={}", existe);
		return new ExisteUsernameResponse(existe);
	}
}
