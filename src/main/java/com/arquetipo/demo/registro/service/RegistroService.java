package com.arquetipo.demo.registro.service;

import com.arquetipo.demo.registro.grpc.RegistroGrpcClient;
import com.arquetipo.demo.registro.web.dto.RegistroRequest;
import com.arquetipo.demo.registro.web.dto.RegistroResponse;
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
@Service
public class RegistroService {

	private final RegistroGrpcClient grpcClient;

	public RegistroService(RegistroGrpcClient grpcClient) {
		this.grpcClient = grpcClient;
	}

	public RegistroResponse registrar(RegistroRequest request) {
		return grpcClient.registrar(request);
	}
}
