package com.arquetipo.demo.conversacion.grpc;

import com.arquetipo.demo.common.Constantes.Grpc;
import com.arquetipo.demo.common.Constantes.Propiedades;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Direccion gRPC del microservicio {@code chat-conversacion}, configurable por entorno
 * (ver {@code application.yml} -> {@code servicios.conversacion} / variables
 * {@code CONVERSACION_GRPC_HOST} y {@code CONVERSACION_GRPC_PORT}).
 */
@ConfigurationProperties(prefix = Propiedades.PREFIJO_SERVICIO_CONVERSACION)
public record ConversacionGrpcProperties(String grpcHost, int grpcPort) {

	public ConversacionGrpcProperties {
		if (grpcHost == null || grpcHost.isBlank()) {
			grpcHost = Grpc.HOST_POR_DEFECTO;
		}
		if (grpcPort <= Grpc.PUERTO_NO_CONFIGURADO) {
			grpcPort = Grpc.PUERTO_CONVERSACION_POR_DEFECTO;
		}
	}
}
