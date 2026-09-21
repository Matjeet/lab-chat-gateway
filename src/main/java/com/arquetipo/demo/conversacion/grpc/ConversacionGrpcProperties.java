package com.arquetipo.demo.conversacion.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Direccion gRPC del microservicio {@code chat-conversacion}, configurable por entorno
 * (ver {@code application.yml} -> {@code servicios.conversacion} / variables
 * {@code CONVERSACION_GRPC_HOST} y {@code CONVERSACION_GRPC_PORT}).
 */
@ConfigurationProperties(prefix = "servicios.conversacion")
public record ConversacionGrpcProperties(String grpcHost, int grpcPort) {

	public ConversacionGrpcProperties {
		if (grpcHost == null || grpcHost.isBlank()) {
			grpcHost = "localhost";
		}
		if (grpcPort <= 0) {
			grpcPort = 9091;
		}
	}
}
