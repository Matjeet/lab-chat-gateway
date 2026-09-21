package com.arquetipo.demo.conversacion.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Canal y stubs gRPC hacia {@code chat-conversacion}.
 *
 * <p>Hacen falta los dos tipos de stub: el asincrono ({@code ConversacionGrpcServiceStub})
 * para {@code Chat}, que es bidi streaming, y el bloqueante para {@code Historial}, que es
 * unario y mas simple de leer como llamada sincrona.
 *
 * <p>Igual que con {@code chat-registro}, la creacion del {@link ManagedChannel} no bloquea
 * ni conecta de inmediato, y va sin TLS ({@code usePlaintext()}) por ser trafico interno.
 */
@Configuration
@EnableConfigurationProperties(ConversacionGrpcProperties.class)
public class ConversacionGrpcClientConfig {

	@Bean(destroyMethod = "shutdown")
	public ManagedChannel conversacionChannel(ConversacionGrpcProperties propiedades) {
		return ManagedChannelBuilder
				.forAddress(propiedades.grpcHost(), propiedades.grpcPort())
				.usePlaintext()
				.build();
	}

	@Bean
	public ConversacionGrpcServiceGrpc.ConversacionGrpcServiceStub conversacionStub(
			ManagedChannel conversacionChannel) {
		return ConversacionGrpcServiceGrpc.newStub(conversacionChannel);
	}

	@Bean
	public ConversacionGrpcServiceGrpc.ConversacionGrpcServiceBlockingStub conversacionBlockingStub(
			ManagedChannel conversacionChannel) {
		return ConversacionGrpcServiceGrpc.newBlockingStub(conversacionChannel);
	}
}
