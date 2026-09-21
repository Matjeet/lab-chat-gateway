package com.arquetipo.demo.registro.service;

import com.arquetipo.demo.registro.grpc.RegistroGrpcClient;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
import com.arquetipo.demo.registro.web.dto.UsuarioResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orquesta el alta de usuarios del lado del gateway: recibe el DTO ya validado por
 * {@link com.arquetipo.demo.registro.web.RegistroController} y lo enruta al microservicio
 * {@code chat-registro} por gRPC.
 *
 * <p>El gateway no aplica ninguna regla de negocio propia (unicidad, hash de contrasena...):
 * esa logica vive en {@code chat-registro} y es la misma para quien lo llame por REST o por
 * gRPC directamente. Esta capa existe para mantener el mismo flujo
 * {@code Controller -> Service -> ...} del resto de servicios y como punto donde encajaria
 * orquestacion futura (p. ej. componer la respuesta con datos de otro microservicio).
 */
@Slf4j
@Service
public class RegistroService {

	private final RegistroGrpcClient grpcClient;

	public RegistroService(RegistroGrpcClient grpcClient) {
		this.grpcClient = grpcClient;
	}

	public RegistroResponse registrar(RegistroRequest request) {
		log.debug(">> registrar(username='{}', email='{}')", request.username(), request.email());
		RegistroResponse respuesta = grpcClient.registrar(request);
		log.debug("<< registrar() -> OK, id={}", respuesta.id());
		return respuesta;
	}

	/**
	 * Resuelve los datos básicos del usuario con ese {@code uid} de Firebase. Se llama con un
	 * {@code uid} ya autenticado y autorizado por
	 * {@link com.arquetipo.demo.registro.web.UsuarioController} — este método (y
	 * {@code chat-registro} por debajo) no ve ni verifica ningún token.
	 */
	public UsuarioResponse obtenerUsuario(String uid) {
		log.debug(">> obtenerUsuario(uid='{}')", uid);
		UsuarioResponse respuesta = grpcClient.buscarUsuarioPorUid(uid);
		log.debug("<< obtenerUsuario() -> OK, username='{}'", respuesta.username());
		return respuesta;
	}
}
