package com.arquetipo.demo.notificacion.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Direccion gRPC del microservicio {@code chat-notificaciones}, configurable por entorno
 * (ver {@code application.yml} -> {@code servicios.notificaciones} / variables
 * {@code NOTIFICACIONES_GRPC_HOST} y {@code NOTIFICACIONES_GRPC_PORT}).
 */
@ConfigurationProperties(prefix = "servicios.notificaciones")
public record NotificacionGrpcProperties(String grpcHost, int grpcPort) {

	public NotificacionGrpcProperties {
		if (grpcHost == null || grpcHost.isBlank()) {
			grpcHost = "localhost";
		}
		if (grpcPort <= 0) {
			grpcPort = 9092;
		}
	}
}
