package com.arquetipo.demo.registro.grpc;

import com.arquetipo.demo.common.Constantes.Grpc;
import com.arquetipo.demo.common.Constantes.Propiedades;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Direccion gRPC del microservicio {@code chat-registro}, configurable por entorno
 * (ver {@code application.yml} -> {@code servicios.registro} / variables
 * {@code REGISTRO_GRPC_HOST} y {@code REGISTRO_GRPC_PORT}).
 */
@ConfigurationProperties(prefix = Propiedades.PREFIJO_SERVICIO_REGISTRO)
public record RegistroGrpcProperties(String grpcHost, int grpcPort) {

	public RegistroGrpcProperties {
		if (grpcHost == null || grpcHost.isBlank()) {
			grpcHost = Grpc.HOST_POR_DEFECTO;
		}
		if (grpcPort <= Grpc.PUERTO_NO_CONFIGURADO) {
			grpcPort = Grpc.PUERTO_REGISTRO_POR_DEFECTO;
		}
	}
}
