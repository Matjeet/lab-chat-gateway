package com.arquetipo.demo.notificacion.grpc;

import com.arquetipo.demo.common.Constantes.Grpc;
import com.arquetipo.demo.common.Constantes.Propiedades;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Direccion gRPC del microservicio {@code chat-notificaciones}, configurable por entorno
 * (ver {@code application.yml} -> {@code servicios.notificaciones} / variables
 * {@code NOTIFICACIONES_GRPC_HOST} y {@code NOTIFICACIONES_GRPC_PORT}).
 */
@ConfigurationProperties(prefix = Propiedades.PREFIJO_SERVICIO_NOTIFICACIONES)
public record NotificacionGrpcProperties(String grpcHost, int grpcPort) {

	public NotificacionGrpcProperties {
		if (grpcHost == null || grpcHost.isBlank()) {
			grpcHost = Grpc.HOST_POR_DEFECTO;
		}
		if (grpcPort <= Grpc.PUERTO_NO_CONFIGURADO) {
			grpcPort = Grpc.PUERTO_NOTIFICACIONES_POR_DEFECTO;
		}
	}
}
