package com.arquetipo.demo.notificacion.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Canal y stub gRPC hacia {@code chat-notificaciones}. Solo hace falta el stub bloqueante:
 * {@code ListaNotificaciones} y {@code ActualizarLeida} son los dos unarios.
 *
 * <p>La creacion del {@link ManagedChannel} no bloquea ni conecta de inmediato (gRPC-Java
 * conecta de forma perezosa en la primera llamada), asi que el arranque del gateway no
 * depende de que {@code chat-notificaciones} este ya levantado. Sin TLS
 * ({@code usePlaintext()}), mismo criterio que el resto de clientes gRPC internos del gateway.
 */
@Configuration
@EnableConfigurationProperties(NotificacionGrpcProperties.class)
public class NotificacionGrpcClientConfig {

	@Bean(destroyMethod = "shutdown")
	public ManagedChannel notificacionChannel(NotificacionGrpcProperties propiedades) {
		return ManagedChannelBuilder
				.forAddress(propiedades.grpcHost(), propiedades.grpcPort())
				.usePlaintext()
				.build();
	}

	@Bean
	public NotificacionGrpcServiceGrpc.NotificacionGrpcServiceBlockingStub notificacionStub(
			ManagedChannel notificacionChannel) {
		return NotificacionGrpcServiceGrpc.newBlockingStub(notificacionChannel);
	}
}
