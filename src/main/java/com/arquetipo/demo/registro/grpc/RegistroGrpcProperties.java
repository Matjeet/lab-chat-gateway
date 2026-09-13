package com.arquetipo.demo.registro.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Direccion gRPC del microservicio {@code chat-registro}, configurable por entorno
 * (ver {@code application.yml} -> {@code servicios.registro} / variables
 * {@code REGISTRO_GRPC_HOST} y {@code REGISTRO_GRPC_PORT}).
 */
@ConfigurationProperties(prefix = "servicios.registro")
public record RegistroGrpcProperties(String grpcHost, int grpcPort) {

	public RegistroGrpcProperties {
		if (grpcHost == null || grpcHost.isBlank()) {
			grpcHost = "localhost";
		}
		if (grpcPort <= 0) {
			grpcPort = 9090;
		}
	}
}
